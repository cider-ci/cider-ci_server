(ns cider-ci.utils.http
  (:require
    [cider-ci.utils.with :as with]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom nil))


;### Http request #############################################################

(defn- request [method url params]
  (logging/debug [method url params])
  (let [basic-auth (:basic_auth @conf)]
    (with/logging
      (logging/debug "http/post" {:url url :basic-auth basic-auth})
      (http-client/request
        (conj {:basic-auth [(:user basic-auth) (:secret basic-auth)]
               :url url 
               :method method
               :insecure? true
               :content-type :json
               :accept :json 
               :socket-timeout 1000  
               :conn-timeout 1000 }
              params)))))

(defn post [url params]
  (logging/debug post [url params])
  (request :post url params))

(defn put [url params]
  (logging/debug put [url params])
  (request :put url params))

(defn patch [url params]
  (logging/debug patch [url params])
   (request :patch url params))


;### Http Basic Authentication ################################################

(defn authenticated? [application password]
  (logging/debug authenticated? [application password])
  (and 
    (= password
       (-> @conf (:basic_auth) (:secret)))
    (keyword application)))

(defn authenticate [handler]
   (wrap-basic-authentication handler authenticated?))


;### Initialize ###############################################################


(defn initialize [new-conf]
  (reset! conf new-conf))



