(ns cider-ci.utils.pending-rows
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as  logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn default-query [table-name]
  [(str "SELECT * FROM "
        table-name
        " ORDER BY created_at ASC, id ASC LIMIT 100")])

(defn build-worker
  [table-name evaluate-row
   & {:keys [query evaluate-row-timeout]
      :or {evaluate-row-timeout 1000
           query (default-query table-name)}}]
  (fn []
    (let [tx (rdbms/get-ds)]
      (->> query
           (jdbc/query tx)
           (map (fn [row]
                  (future (catcher/with-logging {} (evaluate-row row)
                            (jdbc/delete! tx table-name ["id = ?" (:id row)])))))
           (map (fn [ft] (deref ft evaluate-row-timeout nil) ft))
           (map future-cancel)
           doall))))


