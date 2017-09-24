(ns cider-ci.server.client.connection.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.connection.state :as state]
    [cider-ci.server.client.state :as global-state]
    [cider-ci.env]
    [cider-ci.utils.core :refer [str keyword deep-merge presence]]
    [cider-ci.utils.url]

    [cljs-http.client :as http-client]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [cljsjs.moment]
    [clojure.pprint :refer [pprint]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    ))


(defn csrf-token []
  (-> @state/requests* :connection :csrf-token))

;;; autoremove ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn response-success? [resp]
  (<= 200 (-> resp :status) 299))

(defn autoremove [id meta]
  (go (<! (timeout (:autoremove-delay meta)))
      (swap! state/requests* assoc-in
             [:requests id :meta :modal] false )
      (<! (timeout 30000))
      (swap! state/requests* update :requests
             (fn [rqs] (dissoc rqs id)))))

;;; request per se ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def META-DEFAULTS {:autoremove-on-success true
                    :autoremove-delay 1000})

(defn update-progress [id progress-chan]
  (go (let [progress (<! progress-chan)]
        ;(js/console.log (clj->js {:progress progress}))
        (swap! state/requests*
               (fn [state id progress]
                 (if-not (-> state :requests (get id))
                   state
                   (assoc-in state [:requests id :progress] progress)))
               id progress))))

(defn request [id req meta chan callback]
  (go (<! (timeout cider-ci.env/request-delay))
      (let [resp (<! (http-client/request req))]
        (when (-> @state/requests* :requests (get id))
          (swap! state/requests*
                 update-in [:requests id]
                 (fn [req resp]
                   (merge req {:response resp
                               :responted_at (js/moment)}))
                 resp)
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
    (swap! state/requests* assoc-in [:requests id]
           {:request req
            :meta meta
            :id id
            :key id
            :requested_at (js/moment)
            })
    (update-progress id progress-chan)
    (request id req meta chan callback)
    id))

(def query-string http-client/generate-query-string)


;;; ui modal ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def current-modal-request
  (do (reaction
        (let [[id r]  (->> @state/requests* :requests
                           (filter (fn [[_ r]] (or (not= :get (-> r :request :method))
                                                   (-> r :meta :modal))))
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
  (when-let [request @current-modal-request]
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
             :on-click #(swap! state/requests*
                               update-in [:requests]
                               (fn [rx] (dissoc rx (:id request))))}
            [:i.fa.fa-remove] " Dismiss "]
           (when-let [retry-fn (and (= status :error)
                                    (-> request :meta :retry))]
             [:button.btn.btn-primary
              {:on-click  (fn [_]
                            (swap! state/requests*
                                   update-in [:requests]
                                   (fn [rx] (dissoc rx (:id request))))
                            (retry-fn))}
              [:i.fa.fa-recycle] " Retry"])]]]]
       [:div.modal-backdrop {:style {:opacity "0.2"}}]])))


;;; icon ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def fetching?*
  (reaction
    (->> @state/requests*
         :requests
         (map (fn [[id r]] r))
         (map :response)
         (map map?)
         (map not)
         (filter identity)
         first)))

(defn icon-component []
  (if @fetching?*
    [:i.fa.fa-refresh.fa-spin]
    [:i.fa.fa-refresh]))


;;; ui page ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-component []
  (if-not (:debug @global-state/client-state)
    [:div ]
    [:div.requests.debug
     [:hr]
     [:h2 "Requests Debug"]
     [:div.fetching?*
      [:h3 "@fetching?*"]
      [:pre (with-out-str (pprint @fetching?*))] ]
     [:div.requests*
      [:h3 "@requests*"]
      [:pre (with-out-str (pprint @state/requests*))]]]))

(defn request-component [request]
  [:div.request.panel {:class "panel-default" :key (:key request)}
   [:div.panel-heading
    [:h3.panel-title (or (-> request :meta :title) (-> request :id))]]
   [:div.panel-body
    [:pre (with-out-str (pprint request))]
    ]])

(defn requests-component []
  [:section.requests
   (doall
     (for [[_ request] (:requests @state/requests*)]
       [request-component request]
       ))])

(defn page []
  [:div.page.requests
   [:div.title
    [:h1 "Requests"]
    [:p "This page shows the status of the ongoing and recently finished HTTP requests. "
     "It is useful for debugging connection problems."
     ]]
   [requests-component]
   [debug-component]
   ])


