; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.trial
  (:require
    [cider-ci.dispatcher.task :as task]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defonce terminal-states #{"aborted" "failed" "passed"})

;#### utils ###################################################################
(defn get-trial [id]
  (first (jdbc/query (rdbms/get-ds) 
                     ["SELECT * FROM trials WHERE id = ?" id])))


(defmacro wrap-trial-with-issue-and-throw-again [trial title & body]
  `(try 
     ~@body
     (catch Exception e#
       (let [row-data#  {:trial_id (:id ~trial) 
                         :title ~title
                         :description (str (.getMessage e#) "\n\n"  (thrown/stringify e# "\\n"))}]
         (logging/warn ~trial row-data# e#)
         (jdbc/insert! (rdbms/get-ds) "trial_issues" row-data#))
       (throw e#))))


;#### update trial ############################################################
(defn dispatch-update [trial]
  (let [task-id (or (:task_id trial)
                    (:task_id (get-trial (:id trial))))]
    (assert task-id)
    (task/evaluate-and-create-trials {:id task-id})))

(defn update [params]
  (catcher/wrap-with-suppress-and-log-warn
    (let [id (:id params)]
      (try 
        (assert id)
        (let [update-params (select-keys params
                                         [:error :finished_at :result
                                          :scripts :started_at :state ])]
          (jdbc/update! (rdbms/get-ds)
                        :trials update-params
                        ["id = ?" id])
          (dispatch-update (select-keys params [:id :task_id])))
        (catch Exception e
          (jdbc/update! (rdbms/get-ds)
                        :trials 
                        {:state "failed" 
                         :error (thrown/stringify e)}
                        ["id = ?" id])
          (dispatch-update (select-keys params [:id :task_id])))))))


;#### sql helpers #############################################################
(def sql-script-sweep-pending
  " scripts IS NOT NULL
  AND trials.created_at < (SELECT now() - 
  (SELECT max(trial_scripts_retention_time_days) FROM timeout_settings) 
  * interval '1 day') ")

(def sql-in-dispatch-timeout 
  " trials.created_at < (SELECT now() - 
  (SELECT max(trial_dispatch_timeout_minutes)  FROM timeout_settings) 
  * interval '1 Minute') ")

(def sql-in-terminal-state-timeout 
  " trials.created_at < (SELECT now() - 
  (SELECT max(trial_end_state_timeout_minutes)  FROM timeout_settings) 
  * interval '1 Minute') ")

(def sql-not-finished
  " state IN ('pending','dispatching','executing') ")

(def sql-to-be-dispatched
  " state = 'pending' ")



;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


