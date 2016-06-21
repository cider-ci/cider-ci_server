; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.abort
  (:require
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [cider-ci.dispatcher.trials :as trials]

    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.sql :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

;#### abort ###################################################################

(defn- trials-to-be-set-to-aborting [job-id]
  (jdbc/query (get-ds)
              ["SELECT trials.id FROM trials
               JOIN tasks ON trials.task_id = tasks.id
               WHERE tasks.job_id =?
               AND trials.state <> 'aborting'
               AND trials.state NOT IN (?)" job-id
               (->> ["dispatching", "executing"]
                    (clojure.string/join ", "))]))

(defn- next-pending-trial [job-id]
  (->> (jdbc/query (get-ds)
                   ["SELECT trials.id FROM trials
                    JOIN tasks ON trials.task_id = tasks.id
                    WHERE tasks.job_id =?
                    AND trials.state = 'pending'
                    ORDER BY trials.created_at DESC
                    LIMIT 1 " job-id]) first))

(defn- next-processing-trial [job-id]
  (->> (jdbc/query (get-ds)
                   ["SELECT trials.id FROM trials
                    JOIN tasks ON trials.task_id = tasks.id
                    WHERE tasks.job_id =?
                    AND trials.state IN ('dispatching', 'executing')
                    ORDER BY trials.created_at DESC
                    LIMIT 1 " job-id]) first))

(defn- set-pending-trials-to-aborted [job-id params]
  (loop []
    (when-let [trial (next-pending-trial job-id)]
      (trials/update-trial (merge trial params
                                  {:state "aborted"}))
      (recur))))

(defn- set-processing-trials-to-aborted [job-id params]
  (loop []
    (when-let [trial (next-processing-trial job-id)]
      (trials/update-trial (merge trial params
                                  {:state "aborting"}))
      (recur))))

(defn abort-job [job-id params]
  (jdbc/update! (rdbms/get-ds) :jobs
                (merge (select-keys params [:aborted_at :aborted_by])
                       {:state "aborting"})
                ["id = ? " job-id])
  (set-pending-trials-to-aborted job-id params)
  (set-processing-trials-to-aborted job-id params)
  (jdbc/insert! (get-ds) :pending_job_evaluations {:job_id job-id}))


;#### abort detached jobs #####################################################

(def ^:private detached-jobs-subquery-part
  [ "NOT EXISTS"
   (-> (sql-select 1)
       (sql-from :commits)
       (sql-merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
       (sql-merge-join :branches [:= :branches_commits.branch_id :branches.id])
       (sql-merge-join :repositories [:= :branches.repository_id :repositories.id])
       (sql-merge-where [:= :jobs.tree_id  :commits.tree_id]))])

(def ^:private detached-jobs-query
  (-> (sql-select :id)
      (sql-from :jobs)
      (sql-merge-where [:in :jobs.state  ["executing", "pending"]])
      (sql-merge-where detached-jobs-subquery-part)
      sql-format))

(defn- abort-running-detached-jobs []
  (catcher/snatch {}
    (->> (jdbc/query (get-ds) detached-jobs-query)
         (map :id)
         (map #(abort-job % {}))
         doall)))

(defdaemon "abort-running-detached-jobs" 1 (abort-running-detached-jobs))


;#### abort executing trails for dead executors ###############################

(def ^:private executor-dead-condition-query-part
  "(executors.last_ping_at < (now() - interval '1 Minutes'))")

(def ^:private exists-dead-executor-trials-query-part
  [:exists (-> (sql-select 1)
               (sql-from :executors)
               (sql-merge-where [:= :trials.executor_id :executors.id])
               (sql-merge-where (sql-raw executor-dead-condition-query-part)))])

(def ^:private not-exists-executor-query-part
  [:not [:exists
         (-> (sql-select 1)
             (sql-from :executors)
             (sql-merge-where [:= :trials.executor_id :executors.id]))]])

(def ^:private lost-executor-trials-query
  (-> (-> (sql-select :trials.*)
          (sql-from :trials)
          (sql-merge-where [:= :trials.state "executing"])
          (sql-merge-where [:or exists-dead-executor-trials-query-part
                            not-exists-executor-query-part ]))
      sql-format))

(defn set-lost-executor-trials-aborted []
  (->> (jdbc/query (get-ds) lost-executor-trials-query)
       (map #(trials/update-trial (assoc % :state "aborted" :error "Executor went dead or was removed." )))
       doall))

(defdaemon "dead-executor-trials-aborter" 3 (set-lost-executor-trials-aborted))


;#### initialize ##############################################################

(defn initialize []
  (catcher/with-logging {}
    (start-abort-running-detached-jobs)
    (start-dead-executor-trials-aborter)))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
