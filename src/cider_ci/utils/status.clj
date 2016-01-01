(ns cider-ci.utils.status
  (:require
    [cider-ci.utils.runtime :as runtime]

    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]

    [logbug.debug :as debug]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))

(defn status-handler [request]
  (let [rdbms-status (rdbms/check-connection)
        messaging-status (messaging/check-connection)
        memory-status (runtime/check-memory-usage)
        body (json/write-str {:memory memory-status
                              :messaging messaging-status
                              :rdbms rdbms-status
                              })]
    {:status (if (every? identity (map :OK? [rdbms-status messaging-status rdbms-status]))
               200 499)
     :body body
     :headers {"content-type" "application/json; charset=utf-8"}}))

(defn wrap [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))
