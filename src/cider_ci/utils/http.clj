(ns cider-ci.utils.http
  (:require
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [cider-ci.utils.with :as with]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom nil))

(defn post [url params]
  (logging/debug post [url params])
  (let [basic-auth (:basic_auth @conf)]
    (with/logging
      (logging/debug "http/post" {:url url :basic-auth basic-auth})
      (http-client/post 
        url
        (conj {:basic-auth [(:user basic-auth) (:secret basic-auth)]
               :insecure? true
               :content-type :json
               :accept :json 
               :socket-timeout 1000  
               :conn-timeout 1000 }
              params)))))

(defn initialize [new-conf]
  (reset! conf new-conf))


