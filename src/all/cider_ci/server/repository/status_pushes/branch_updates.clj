; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.status-pushes.branch-updates
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.repository.status-pushes.shared :refer [base-query]]
    [cider-ci.server.repository.status-pushes.remotes :refer [dispatch]]


    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.row-events :as row-events]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))


;### Branch update ############################################################

(defn get-repos-and-jobs-by-branch-update [branch-id]
  (let [query (-> base-query
                  (sql/merge-join :branches [:= :branches.repository_id :repositories.id])
                  (sql/merge-join :commits [:= :commits.id :branches.current_commit_id])
                  (sql/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
                  (sql/merge-where [:= :branches.id branch-id])
                  sql/format)]
    (jdbc/query
      (rdbms/get-ds)
      query)))

(defn evaluate-branch-update [branch-id]
  (dispatch (get-repos-and-jobs-by-branch-update branch-id)))


;### Listen to branch updates #################################################

(def last-processed-branch-update (atom nil))

(defdaemon "process-branch-updates" 1
  (row-events/process "branch_update_events" last-processed-branch-update
                      (fn [row] (evaluate-branch-update (:branch_id row)))))

;### Initialize ###############################################################


(defn initialize []
  (start-process-branch-updates))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
