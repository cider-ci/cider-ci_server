; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.trial
  (:require
    [cider-ci.dispatcher.task :as task]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.rdbms.conversion :as rdbms.conversion]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

(defonce conf (atom nil))

(defonce terminal-states #{"aborted" "failed" "passed"})

;#### utils ###################################################################
(defn get-trial [id]
  (first (jdbc/query (rdbms/get-ds) 
                     ["SELECT * FROM trials WHERE id = ?::UUID" id])))

;#### update trial ############################################################
(defn dispatch-update [trial]
  (let [task-id (or (:task_id trial)
                    (:task_id (get-trial (:id trial))))]
    (assert task-id)
    (task/evaluate-and-create-trials {:id task-id})))

(defn update [params]
  (with/suppress-and-log-warn
    (let [id (:id params)]
      (try 
        (assert id)
        (let [update-params (select-keys params
                                         [:state :started_at :finished_at :error :scripts :result])
              converted-params (rdbms.conversion/convert-parameters :trials update-params)]
          (logging/debug {:params params :update-params update-params 
                          :converted-params converted-params})
          (jdbc/update! (rdbms/get-ds)
                        :trials converted-params
                        ["id = ?::UUID" id])
          (dispatch-update (select-keys params [:id :task_id])))
        (catch Exception e
          (jdbc/update! (rdbms/get-ds)
                        :trials 
                        {:state "failed" 
                         :error (exception/stringify e)}
                        ["id = ?::UUID" id])
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


;#### initialize ##############################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


