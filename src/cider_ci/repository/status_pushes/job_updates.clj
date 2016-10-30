; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.status-pushes.job-updates
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.status-pushes.remotes :refer [dispatch]]
    [cider-ci.repository.status-pushes.shared :refer [base-query]]

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



;### Job update ###############################################################

(defn build-repos-and-jobs-query-by-job-id [job-id]
  (-> base-query
      (sql/merge-join :branches [:= :branches.repository_id :repositories.id])
      (sql/merge-join :branches_commits [:= :branches.id :branches_commits.branch_id])
      (sql/merge-join :commits [:= :commits.id :branches_commits.commit_id])
      (sql/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
      (sql/merge-where [:= :jobs.id job-id])
      sql/format))

(defn get-repos-and-jobs-by-job-update [job-id]
  (jdbc/query
    (rdbms/get-ds)
    (build-repos-and-jobs-query-by-job-id job-id)))

(defn evaluate-job-update [job-id]
  (dispatch (get-repos-and-jobs-by-job-update job-id)))


;### Listen to job updates ####################################################

(def last-processed-job-update (atom nil))

(defdaemon "process-job-updates" 1
  (row-events/process "job_state_update_events" last-processed-job-update
                      (fn [row] (evaluate-job-update (:job_id row)))))


;### Initialize ###############################################################

(defn initialize []
  (start-process-job-updates))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
