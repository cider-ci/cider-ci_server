(ns cider-ci.repository.request
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:refer-clojure :exclude [send-off])
  (:require
    [reagent.core :as r]
    [cider-ci.repository.state :as state]
    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.url]
    [cljs-http.client :as http]
    ))


(def last-request (r/atom {}))

(defn csrf-token []
  (-> @state/client-state :ws-connection deref :csrf-token))

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
