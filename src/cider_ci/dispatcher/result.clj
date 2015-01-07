; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.result
  (:require
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


(defn- get-trial-result-to-be-passed-on [task-id]
  (-> (jdbc/query (rdbms/get-ds) 
                  [ "SELECT trials.result FROM trials
                    WHERE trials.task_id = ? 
                    AND state IN ('passed', 'failed', 'aborted')
                    AND result IS NOT NULL
                    ORDER BY CASE WHEN state = 'passed' THEN 1 
                                  WHEN state = 'failed' THEN 2 
                                  WHEN state = 'aborted' THEN 3
                                  END ASC, 
                              created_at DESC LIMIT 1 " 
                   task-id]) first :result))

(defn- get-task-result [task-id]
  (-> (jdbc/query (rdbms/get-ds) 
                  [ "SELECT tasks.result FROM tasks
                     WHERE tasks.id = ?" task-id])
      first :result))

(defn- executions-base-query [task-id]
  (-> (hh/select :*)
      (hh/from :executions)
      (hh/merge-join :tasks [:= :tasks.execution_id :executions.id])
      (hh/merge-where [:= :tasks.id task-id])))

;(execution-id-for-task "64e77f59-bc02-5c3a-aa0a-5400ea802d75")
(defn- execution-id-for-task [task-id]
  (-> (executions-base-query task-id)
      (hh/select [:executions.id :id])
      hc/format
      (#(jdbc/query (rdbms/get-ds) %))
      first :id)) 


;(count-siblings-of-task "64e77f59-bc02-5c3a-aa0a-5400ea802d75")
(defn- count-siblings-of-task [task-id]
  (-> (executions-base-query :_)
      (hh/where [:= :executions.id (execution-id-for-task task-id)])
      (hh/select [:%count.* :count])
      hc/format 
      (#(jdbc/query (rdbms/get-ds) %))
      first :count))


(defn- task-has-no-sibblings [task-id]
  "Returns true if and only if the execution has no further tasks than the
  given."
  (= 1 (count-siblings-of-task task-id)))


(defn- update-execution-result [task-id result]
  "Updates the result property of the execution belonging to the given task if
  and only if the existing result of the execution is not equal"
  (let [query (-> (executions-base-query task-id)
                  (hh/limit 1)
                  (hc/format))
        execution (-> (jdbc/query (rdbms/get-ds) query) first)]
    (when-not (= result (:result execution))
      (jdbc/update! (rdbms/get-ds)
                    :executions  {:result result}
                    ["id = ?" (:id execution)]))))

(defn update-task-and-execution-result [task-id]
  "Queries the trials belonging to the given task for the result property to be
  passed on (see get-trial-result-to-be-passed-on) and sets this result" 
  (when-let [trial-result (get-trial-result-to-be-passed-on task-id)]
    (let [task-result (get-task-result task-id)]
      (when-not (= trial-result task-result)
        (jdbc/update! (rdbms/get-ds) :tasks {:result trial-result}
                      ["id = ?" task-id])
        (when (task-has-no-sibblings task-id)
          (update-execution-result task-id trial-result))))))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
