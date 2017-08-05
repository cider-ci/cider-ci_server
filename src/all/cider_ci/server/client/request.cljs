(ns cider-ci.server.client.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.state :as state]
    [cider-ci.env]
    [cider-ci.utils.core :refer [str keyword deep-merge presence]]
    [cider-ci.utils.url]

    [cljs.core.async :as async]
    [cljs-http.client :as http]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :refer [timeout]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    ))

(defn csrf-token []
  (-> @state/client-state :connection :csrf-token))


;;; autoremove ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn response-success? [resp]
  (<= 200 (-> resp :status) 299))

(defn autoremove [id meta]
  (go (<! (timeout (:autoremove-delay meta)))
      (swap! state/client-state assoc-in
             [:requests id :meta :modal] false )
      (<! (timeout 30000))
      (swap! state/client-state update :requests
             (fn [rqs] (dissoc rqs id)))))

;;; request per se ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def META-DEFAULTS {:modal true
                    :autoremove-on-success true
                    :autoremove-delay 1000})

(defn update-progress [id progress-chan]
  (go (let [progress (<! progress-chan)]
        ;(js/console.log (clj->js {:progress progress}))
        (swap! state/client-state
               (fn [state id progress]
                 (if-not (-> state :requests (get id))
                   state
                   (assoc-in state [:requests id :progress] progress)))
               id progress))))

(defn request [id req meta chan callback]
  (go (<! (timeout cider-ci.env/request-delay))
      (let [resp (<! (http/request req))]
        (when (-> @state/client-state :requests (get id))
          (swap! state/client-state assoc-in [:requests id :response] resp)
          (when (and (response-success? resp)
                     (:autoremove-on-success meta))
            (autoremove id meta)))
        (when callback (callback resp))
        (when chan (>! chan resp)))))

(defn send-off [req-opts meta-opts & {:keys [callback chan]
                                      :or {callback nil chan nil}}]
  (let [id (uuid/uuid-string (uuid/make-random-uuid))
        progress-chan (async/chan)
        req (deep-merge {:method :post
                         :headers {"accept" "application/json"
                                   "X-CSRF-Token" (csrf-token)}
                         :progress progress-chan}
                        req-opts)
        meta (deep-merge META-DEFAULTS meta-opts)]
    (swap! state/client-state assoc-in [:requests id]
           {:request req :meta meta})
    (update-progress id progress-chan)
    (request id req meta chan callback)
    id))


;;; ui modal ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def modal-request
  (do (reaction
        (let [[id r]  (->> @state/client-state :requests
                           (filter (fn [[_ r]] (-> r :meta :modal)))
                           first)]
          (when r
            (assoc r :id id))))))

(defn progress-bar-component [status bootstrap-status request]
  (let [progress-part (if-let [progress (-> request :progress)]
                        (if-let [total (-> progress :total)]
                          (max 0.1 (/ (:loaded progress) total))
                          0.1)
                        0.1)]
    [:div.progress
     [:div.progress-bar
      {:class (case status
                :pending "progress-bar-striped active"
                :success "progress-bar-success"
                :error "progress-bar-danger")
       :style {:width (str (* progress-part 100) "%")}}]]))

(defn modal-component []
  (when-let [request @modal-request]
    (let [status (cond (= nil (-> request :response)) :pending
                       (-> request :response :success) :success
                       :else :error)
          bootstrap-status (case status
                             :pending :default
                             :success :success
                             :error :danger)]
      [:div {:style {:opacity (case status
                                :pending "0.8"
                                "1.0")
                     :z-index 10000}}
       [:div.modal {:style {:display "block"
                            :z-index 10000}}
        [:div.modal-dialog
         [:div.modal-content {:class (str "modal-" bootstrap-status)}
          (when-not (= bootstrap-status :pending)
            [:div.modal-header
             [:h4 (str " Request "
                       (when-let [title (-> request :meta :title)]
                         (str " \"" title "\" "))
                       (case status
                         :error " ERROR "
                         :success " OK "
                         :pending " PENDING "
                         nil))
              (-> request :response :status)]])
          [:div.modal-body
           (when (= status :pending)
             [progress-bar-component status bootstrap-status request])
           (if-let [body (-> :response :body presence)]
             [:p body])]
          [:div.modal-footer
           [:div.clearfix]
           [:button.btn
            {:class (str "btn-" bootstrap-status)
             :on-click #(swap! state/client-state
                               update-in [:requests]
                               (fn [rx] (dissoc rx (:id request))))}
            "Dismiss"]]]]]
       [:div.modal-backdrop {:style {:opacity "0.2"}}]])))
