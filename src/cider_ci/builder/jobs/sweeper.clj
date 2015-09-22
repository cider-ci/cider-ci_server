; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.sweeper
  (:require
    [cider-ci.utils.config :refer [get-config parse-config-duration-to-seconds]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [honeysql.sql :refer :all]
    ))

(defn- get-job-retention-interval []
  (catcher/wrap-with-suppress-and-log-warn
    (when-let [job-retention-duration-secs
               (parse-config-duration-to-seconds :job_retention_duration)]
      (str job-retention-duration-secs " Seconds "))))

(defn- job-overdue-query-part [job-retention-interval]
  (str "(jobs.updated_at < (now() - interval '" job-retention-interval "'))"))

(defn- job-state-condition-query-part []
  [:in :jobs.state (-> (get-config) :constants :STATES :FINISHED)])

(def ^:private job-not-directly-releated-to-branch-query-part
  [:not [:exists
         (-> (sql-select 1)
             (sql-from :commits)
             (sql-where [:= :jobs.tree_id :commits.tree_id])
             (sql-merge-join :branches [:= :branches.current_commit_id
                                        :commits.id]))]])

(defn- to-be-swept-jobs-query []
  (when-let [job-retention-interval (get-job-retention-interval)]
    (-> (-> (sql-select :jobs.id)
            (sql-from :jobs)
            (sql-merge-where (sql-raw (job-overdue-query-part
                                        job-retention-interval)))
            (sql-merge-where (job-state-condition-query-part))
            (sql-merge-where job-not-directly-releated-to-branch-query-part)
            )sql-format)))

(defn- delete-jobs [job-ids]
  (->> (-> (sql-delete-from :jobs)
           (sql-merge-where [:in :jobs.id job-ids])
           (sql-returning :jobs.name :jobs.id :jobs.tree_id :jobs.key)
           sql-format)
       (jdbc/query (get-ds))))

(defn- sweep-jobs []
  (catcher/wrap-with-suppress-and-log-error
    (when-let [ids-query (to-be-swept-jobs-query)]
      (when-let [job-ids (->> ids-query
                              (jdbc/query (get-ds))
                              (map :id)
                              seq)]
        (delete-jobs job-ids)))))

(defdaemon "sweep-jobs" 10 (sweep-jobs))

(defn initialize []
  (start-sweep-jobs))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
