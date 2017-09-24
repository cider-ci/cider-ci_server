(ns cider-ci.server.client.connection
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.connection.socket :as socket]
    ))

(defn page []
  [:div.page.connection
   [:h1 "Connection"]
   ])

; TODO spinner for sockets
; TODO reload for requests
(defn connection-li []
  [:li
   [:a {:href (routes/connection-path)}
    [:i.fa.fa-spinner] " Connection" ]])

(defn requests-li []
  [:li
   [:a {:href "#"}
    [:i.fa.fa-refresh]
    "Requests"
    ]])

(def requests-icon-component request/icon-component)

(def socket-icon-component socket/icon-component)


(defn init []
  (socket/init)
  )
