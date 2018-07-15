(ns cider-ci.utils.ring-exception
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
  ))

(defn get-cause [e]
  (try (if (instance? java.sql.BatchUpdateException e)
         (if-let [n (.getNextException e)]
           (get-cause n) e)
         (if-let [c (.getCause e)]
           (get-cause c) e))
       (catch Throwable _ e)))

(defn wrap [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable ex
        (let [cause (get-cause ex)]
          (logging/warn (.getMessage ex) (thrown/stringify cause))
          (cond
            (and (instance? clojure.lang.ExceptionInfo ex)
                 (contains? (ex-data ex) :status)
                 ){:status (:status (ex-data ex))
                   :headers {"Content-Type" "text/plain"}
                   :body (str (.getMessage ex)
                              " \n"
                              "The server logs may contain additional information.")}
            (instance? org.postgresql.util.PSQLException
                       ex){:status 409
                          :body (.getMessage ex)}
            :else {:status 500
                   :headers {"Content-Type" "text/plain"}
                   :body "Unclassified error, see the server logs for details."}))))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
