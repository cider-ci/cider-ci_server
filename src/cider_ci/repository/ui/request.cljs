(ns cider-ci.repository.ui.request
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:refer-clojure :exclude [send-off])
  (:require
    [reagent.core :as r]
    [cider-ci.client.state :as state]
    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.url]
    [cljs-http.client :as http]
    ))

(def last-request (r/atom {}))

(defn csrf-token []
  (-> @state/client-state :connection :csrf-token))

(defn send-off [opts _stores]
  (let [req (deep-merge {:method :post
                         :headers {"accept" "application/json"
                                   "X-CSRF-Token" (csrf-token)}}
                        opts)
        stores (conj _stores {:keys [] :db last-request})]
    (doseq [{ks :keys db :db} stores]
      (if (empty? ks) ; delete old request/response keys
        (swap! db dissoc :request :response)
        (swap! db update-in ks #(dissoc % :request :response)))
      (if (empty? ks) ; set new request
        (swap! db assoc :request req)
        (swap! db update-in ks #(assoc % :request req))))
    (go (let [resp (<! (http/request req))]
          (doseq [{ks :keys db :db} stores]
            (if (empty? ks)
              (swap! db assoc :response resp)
              (swap! db update-in ks #(assoc % :response resp))))))))


;##############################################################################

(defn- icon [request]
  (if (empty? (:response request)) [:i.fa.fa-fw.fa-refresh.fa-spin]
    (condp contains? (-> request :response :status)
      (set (range 200 299)) [:i.fa.fa-fw.fa-check-circle]
      (set (range 300 599)) [:i.fa.fa-fw.fa-warning]
      [:i.fa.fa-fw.fa-question-circle]
      )))

(defn- alert-class [request]
  (condp contains? (-> request :response :status)
    (set (range 200 299)) "alert-success"
    (set (range 400 599)) "alert-danger"
    "alert-warning"))

(defn dismiss-alert [id]
  (swap! state/client-state
         (fn [db] (update-in db [:requests] dissoc id))))

(defn- request-alert [id request]
  [:div.request.alert {:class (alert-class request)}
   [:button.close {:type "button"
                   :on-click #(dismiss-alert id)}
    [:span [:i.fa.fa-close]]]
   [:h4 [icon request]
    (when-let [status (-> request :response :status)]
      status) " Request "]
   [:h5 (-> request :meta :title)]
   ;[:pre (.stringify js/JSON (clj->js request) nil 2)]
   ])

(defn requests []
  [:div.requests
   (when-not (empty? (-> @state/client-state :requests))
     [:h3 "Requests"])
   (for [[id req] (-> @state/client-state :requests reverse)]
     ^{:key id} [request-alert id req])])

;##############################################################################

(defn send-off2 [req-opts meta-req & {:keys [callback]
                                      :or {callback nil}}]
  (let [req (deep-merge {:method :post
                         :headers {"accept" "application/json"
                                   "X-CSRF-Token" (csrf-token)}}
                        req-opts)
        id (rand)]
    (swap! state/client-state assoc-in [:requests id] {:request req
                                                       :meta meta-req})
    (go (let [resp (<! (http/request req))]
          (when (-> @state/client-state :requests (get id))
            (swap! state/client-state assoc-in [:requests id :response] resp)
            (when (<= 200 (-> resp :status) 299)
              (js/setTimeout (fn [] (swap! state/client-state
                                           #(update-in %1 [:requests] dissoc %2) id))
                             5000)))
          (when callback (callback resp))))
    id))


