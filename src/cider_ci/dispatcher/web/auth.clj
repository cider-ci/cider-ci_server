(ns cider-ci.dispatcher.web.auth
  (:require
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.utils.ring :refer [web-ex]]

    [honeysql.sql :refer :all]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.debug :as debug]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [cider_ci WebstackException]
    ))

(defn valid-token-for-trial? [trial-id token]
  (let [query (-> (sql-select true)
                  (sql-from :trials)
                  (sql-merge-where [:= :token token])
                  (sql-merge-where [:= :id trial-id])
                  (sql-merge-where (sql-raw "(trials.updated_at > (now() - interval '24 Hours'))"))
                  sql-format)]
    (->> query (jdbc/query (get-ds)) first)))

(defn validate-trial-token! [request trial-id]
  (let [{{token "trial-token"} :headers} request]
    (when-not token
      (throw (web-ex "Trial-token header is missing!"
                      {:status 403
                       :body (json/write-str {:message "trial-token header required"})
                       :content-type "application/json"})))
    (when-not (valid-token-for-trial? trial-id token)
      (throw (web-ex "Token is not valid!"
                      {:status 403
                       :body (json/write-str {:message "token is not valid"})
                       :content-type "application/json"})))))

