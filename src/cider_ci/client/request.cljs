(ns cider-ci.client.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.client.state :as state]
    [cider-ci.env]
    [cider-ci.utils.core :refer [str keyword deep-merge]]
    [cider-ci.utils.url]

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

(defn send-off [req-opts meta-opts & {:keys [callback]
                                      :or {callback nil}}]
  (let [req (deep-merge {:method :post
                         :headers {"accept" "application/json"
                                   "X-CSRF-Token" (csrf-token)}}
                        req-opts)
        meta (deep-merge META-DEFAULTS meta-opts)
        id (uuid/uuid-string (uuid/make-random-uuid))]
    (swap! state/client-state assoc-in [:requests id]
           {:request req :meta meta})
    (go (<! (timeout cider-ci.env/request-delay))
        (let [resp (<! (http/request req))]
          (when (-> @state/client-state :requests (get id))
            (swap! state/client-state assoc-in [:requests id :response] resp)
            (when (and (response-success? resp)
                       (:autoremove-on-success meta))
              (autoremove id meta)))
          (when callback (callback resp))))
    id))


;;; ui modal ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def modal-request
  (do (reaction
        (let [[id r]  (->> @state/client-state :requests
                           (filter (fn [[_ r]] (-> r :meta :modal)))
                           first)]
          (when r
            (assoc r :id id))))))


(defn modal []
  (when-let [request @modal-request]
    (let [bootstrap-status (cond (= nil (-> request :response)) :pending
                                 (-> request :response :success) :success
                                 :else :danger)]
      [:div {:style {:opacity (case bootstrap-status :pending "0.8" "1.0")}}
       [:div.modal {:style {:display "block"}}
        [:div.modal-dialog
         [:div.modal-content {:class (str "modal-" bootstrap-status)}
          (when-not (= bootstrap-status :pending)
            [:div.modal-header
             [:h4 (str " Request "
                       (when-let [title (-> request :meta :title)]
                         (str " \"" title "\" "))
                       (case bootstrap-status
                         :danger " ERROR "
                         nil))
              (-> request :response :status)]])
          [:div.modal-body
           (if-let [response (:response request)]
             [:p (-> response :body)]
             [:p.text-center [:i.fa.fa-fw.fa-5x.fa-spin.fa-circle-o-notch]])]
          [:div.modal-footer
           [:div.clearfix]
           [:button.btn
            {:class (str "btn-" bootstrap-status)
             :on-click #(swap! state/client-state
                               update-in [:requests]
                               (fn [rx] (dissoc rx (:id request))))}
            "Dismiss"]
           ]]]]
       [:div.modal-backdrop {:style {:opacity "0.2"}}]])))
