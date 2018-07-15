(ns cider-ci.utils.http
  (:refer-clojure :exclude [get str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.map]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


;### Http request #############################################################

(defn request [method url params]
  (logging/debug [method url params])
  (let [{username :username password :password} (:basic_auth (get-config))
        req-map (conj {:basic-auth [(to-cistr username) (to-cistr password)]
                       :url url
                       :method method
                       :insecure? true
                       :content-type :json
                       :accept :json
                       :socket-timeout 1000
                       :conn-timeout 1000
                       :as :auto}
                      params)]
    (logging/debug 'request req-map)
    (http-client/request req-map)))

(defn get [url params]
  (logging/debug get [url params])
  (request :get url params))

(defn post [url params]
  (logging/debug post [url params])
  (request :post url params))

(defn put [url params]
  (logging/debug put [url params])
  (request :put url params))

(defn patch [url params]
  (logging/debug patch [url params])
   (request :patch url params))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


