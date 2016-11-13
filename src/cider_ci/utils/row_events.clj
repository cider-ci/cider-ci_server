(ns cider-ci.utils.row-events
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as  logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn process-last-row [table-name state-atom row-handler]
  (swap! state-atom
         (fn [_]
           (when-let [last-row (I>> identity-with-logging
                                    [(str "SELECT * FROM " table-name
                                          " ORDER BY created_at DESC, id LIMIT 1")]
                                    (jdbc/query (rdbms/get-ds))
                                    first)]
             (snatch {} (row-handler last-row))
             last-row))))



; 1. we compare to the timestamp of the row with the given id instead of
;   the saved created_at because the latter might not be exactly the same
;   (loosing bits due to coercion)
; 2. this row might not even exist anymore; e.g. because it has been removed
;     by the sweeper or something else (truncate); in that case we query all
;     events of the last 24 hours as a fallback

(defn process-new-rows [table-name state-atom row-handler]
  (swap! state-atom
         (fn [last-processed-row]
           (or (I>> identity-with-logging
                    [(str "SELECT * FROM " table-name
                          " WHERE created_at > "
                          "  (SELECT COALESCE(max(created_at), (now() - interval '24 hours')) "
                          "   FROM " table-name " WHERE id = ?)"
                          " ORDER BY created_at ASC LIMIT 1000")
                     (:id last-processed-row)]
                    (jdbc/query (rdbms/get-ds))
                    (map (fn [row] (snatch {} (row-handler row)) row))
                    last)
               last-processed-row))))

(defn process
  [table-name state-atom row-handler]
  (locking state-atom
    (if-not @state-atom
      (process-last-row table-name state-atom row-handler)
      (process-new-rows table-name state-atom row-handler))))

