; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.abort
  (:require
    [cider-ci.dispatcher.job :as job]
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [cider-ci.dispatcher.trial :as trial]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [honeysql.format :as hsql-format]
    [honeysql.helpers :as hsql-helpers]
    [honeysql.types :as hsql-types]
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

(defn- set-pending-trials-to-aborted [job-id]
  (loop []
    (when-let [trial (next-pending-trial job-id)]
      (trial/update (assoc trial :state "aborted"))
      (recur))))

(defn- set-processing-trials-to-aborted [job-id]
  (loop []
    (when-let [trial (next-processing-trial job-id)]
      (trial/update (assoc trial :state "aborting"))
      (recur))))

(defn abort-job [job-id]
  (jdbc/execute! (get-ds)
                 ["UPDATE jobs
                  SET state = 'aborting'
                  WHERE id = ? " job-id])
  (set-pending-trials-to-aborted job-id)
  (set-processing-trials-to-aborted job-id)
  (job/evaluate-and-update job-id))


;#### abort detached jobs #####################################################

(def ^:private detached-jobs-subquery-part
  [ "NOT EXISTS"
   (-> (hsql-helpers/select 1)
       (hsql-helpers/from :commits)
       (hsql-helpers/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
       (hsql-helpers/merge-join :branches [:= :branches_commits.branch_id :branches.id])
       (hsql-helpers/merge-join :repositories [:= :branches.repository_id :repositories.id])
       (hsql-helpers/merge-where [:= :jobs.tree_id  :commits.tree_id]))])

(def ^:private detached-jobs-query
  (-> (hsql-helpers/select :id)
      (hsql-helpers/from :jobs)
      (hsql-helpers/merge-where [:in :jobs.state  ["executing","pending"]])
      (hsql-helpers/merge-where detached-jobs-subquery-part)
      hsql-format/format))

(defn- abort-running-detached-jobs [_]
  (catcher/wrap-with-suppress-and-log-error
    (->> (jdbc/query (get-ds) detached-jobs-query)
         (map :id)
         (map abort-job)
         doall)))

(defn initialize []
  (catcher/wrap-with-log-error
    (messaging/listen "repository.updated" #'abort-running-detached-jobs)))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
