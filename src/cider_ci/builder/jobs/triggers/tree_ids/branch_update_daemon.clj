; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.triggers.tree-ids.branch-update-daemon
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.builder.jobs.triggers.tree-ids.branch-update :as branch-update]

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
      (sql/merge-where [:<> :branch_id nil])
      (sql/limit 100)
      sql/format))

(defn- sql-delete-statement [ids]
  (-> (honeysql.helpers/delete-from :tree_id_notifications)
      (sql/merge-where [:in :tree_id_notifications.id ids])
      sql/format))

(defn- eval-branch-update-notifications []
  (jdbc/with-db-transaction [tx (rdbms/get-ds)]
    (when-let [ids (->> ids-query (jdbc/query tx) (map :id) seq)]
      (branch-update/build-and-trigger tx ids)
      (jdbc/execute! tx (sql-delete-statement ids)))))

(defdaemon "eval-branch-update-notifications" 1 (eval-branch-update-notifications))

(defn initialize []
  (start-eval-branch-update-notifications))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/re-apply-last-argument #'eval-branch-update-notifications)
;(debug/debug-ns *ns*)

