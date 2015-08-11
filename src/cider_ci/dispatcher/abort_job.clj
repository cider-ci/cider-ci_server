; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.abort-job
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



(defn abort [job-id]
  (jdbc/execute! (get-ds)
                 ["UPDATE jobs
                  SET state = 'aborting'
                  WHERE id = ? " job-id])
  (set-pending-trials-to-aborted job-id)
  (set-processing-trials-to-aborted job-id)
  (job/evaluate-and-update job-id))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
