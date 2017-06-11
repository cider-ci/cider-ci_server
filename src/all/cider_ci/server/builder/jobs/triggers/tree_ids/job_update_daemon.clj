; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.jobs.triggers.tree-ids.job-update-daemon
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.builder.jobs.triggers.tree-ids.job-update :as job-update]

    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.duration :as duration]
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.rdbms :as rdbms]


    [honeysql.core :as sql]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(def ids-query
  (-> (sql/select :id)
      (sql/from :tree_id_notifications)
      (sql/order-by [:created_at :asc])
      (sql/merge-where [:<> :job_id nil])
      (sql/limit 100)
      sql/format))

(defn- sql-delete-statement [ids]
  (-> (honeysql.helpers/delete-from :tree_id_notifications)
      (sql/merge-where [:in :tree_id_notifications.id ids])
      sql/format))

(defn- evaluate-tree-id-notifications []
  (jdbc/with-db-transaction [tx (rdbms/get-ds)]
    (when-let [ids (->> ids-query (jdbc/query tx) (map :id) seq)]
      (job-update/build-and-trigger tx ids)
      (jdbc/execute! tx (sql-delete-statement ids)))))

(defdaemon "evaluate-tree-id-notifications" 1 (evaluate-tree-id-notifications))

(defn initialize []
  (start-evaluate-tree-id-notifications))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/re-apply-last-argument #'evaluate-tree-id-notifications)
;(debug/debug-ns *ns*)

