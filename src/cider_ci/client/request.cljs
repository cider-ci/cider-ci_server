(ns cider-ci.client.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.utils.core :refer [str keyword deep-merge]]
    [cider-ci.utils.url]
    [cider-ci.client.state :as state]

    [cljs-http.client :as http]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    ))

(defn csrf-token []
  (-> @state/client-state :connection :csrf-token))

;;; success auto removal ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def REMOVE-SUCCESS-RETENTION 2000)
(def DEC-INTERVAL 200)

(defn dec-counter [c]
  (- c DEC-INTERVAL ))

(defn decrement-or-remove [current-client-state id]
  (if-let [request (-> current-client-state :requests (get id))]
    ; add 1/2 sec because it looks better
    (if (>= -500 (-> request :meta :count-down))
      (update-in current-client-state [:requests] dissoc id)
      (update-in current-client-state [:requests id :meta :count-down] dec-counter))
    current-client-state))

(defn remove-count-down [id]
  (let [new-state (swap! state/client-state
                         (fn [cs] (decrement-or-remove cs id)))]
    (when (-> new-state :requests (get id))
      (js/setTimeout #(remove-count-down id) DEC-INTERVAL))))


;;; request per se ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def META-DEFAULTS {:modal true})

(defn send-off [req-opts meta-req & {:keys [callback]
                                      :or {callback nil}}]
  (let [req (deep-merge {:method :post
                         :headers {"accept" "application/json"
                                   "X-CSRF-Token" (csrf-token)}}
                        req-opts)
        id (rand)]
    (swap! state/client-state assoc-in [:requests id]
           {:request req :meta (deep-merge META-DEFAULTS meta-req)})
    (go (let [resp (<! (http/request req))]
          (when (-> @state/client-state :requests (get id))
            (swap! state/client-state assoc-in [:requests id :response] resp)
            (when (<= 200 (-> resp :status) 299)
              (swap! state/client-state assoc-in
                     [:requests id :meta :count-down]
                     REMOVE-SUCCESS-RETENTION)
              (remove-count-down id)))
          (when callback (callback resp))))
    id))


;;; ui modal ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def modal-request
  (do
    (reaction
      (let [[id r]  (->> @state/client-state :requests
                         (filter (fn [[_ r]] (-> r :meta :modal)))
                         first)]
        (when r
          (assoc r :id id))))
    ;(reaction {:id 5 :meta {:modal true :count-down 0} :response {:success true}})
    ))


(def modal-autoremove-fraction
  (reaction (when-let [request @modal-request]
              (/ (- REMOVE-SUCCESS-RETENTION
                    (-> request :meta :count-down))
                 REMOVE-SUCCESS-RETENTION))))

(defn modal []
  (when-let [request @modal-request]
    (let [bootstrap-status (cond (= nil (-> request :response)) :pending
                                 (-> request :response :success) :success
                                 :else :danger)]
      [:div
       [:div.modal {:style {:display "block"}}
        [:div.modal-dialog
         [:div.modal-content {:class (str "modal-" bootstrap-status)}
          [:div.modal-header
           [:h4 (str " Request "
                     (when-let [title (-> request :meta :title)]
                       (str " \"" title "\" "))
                     (case bootstrap-status
                       :danger " ERROR "
                       nil))
            (-> request :response :status)]]
          [:div.modal-body
           [:p (-> request :response :body)]]
          [:div.modal-footer
           [:div
            (when (= bootstrap-status :success)
              (let [pc (gstring/format
                         "%.0f%"
                         (min 100 (* 100 @modal-autoremove-fraction)))]
                [:div.progress
                 [:div.progress-bar.progress-bar-success
                  {:style {:width  pc}}
                  pc ]]))]
           [:div.clearfix]
           [:button.btn
            {:class (str "btn-" bootstrap-status)
             :on-click #(swap! state/client-state
                               update-in [:requests]
                               (fn [rx] (dissoc rx (:id request))))}
            "Dismiss"]
           ]]]]
       [:div.modal-backdrop {:style {:opacity "0.2"}}]])))
