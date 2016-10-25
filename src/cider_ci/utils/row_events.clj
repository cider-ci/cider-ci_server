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

(defn process
  [table-name state-atom row-handler]
  (when-not @state-atom
    (when-let [last-row (I>> identity-with-logging
                             [(str "SELECT * FROM " table-name
                                   " ORDER BY created_at DESC, id LIMIT 1")]
                             (jdbc/query (rdbms/get-ds))
                             first)]
      (snatch {} (row-handler last-row))
      (reset! state-atom last-row)))
  (when-let [after-row @state-atom]
    (if-let [lst (I>> identity-with-logging
                      [(str "SELECT * FROM " table-name
                            " WHERE created_at >= ? AND id != ?"
                            " ORDER BY created_at ASC , id LIMIT 100")
                       (:created_at after-row) (:id after-row)]
                      (jdbc/query (rdbms/get-ds))
                      (map (fn [row] (snatch {} (row-handler row)) row))
                      last)]
      (reset! state-atom lst)
      (reset! state-atom after-row))))
