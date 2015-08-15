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
    [cider-ci.utils.map :as map :refer [deep-merge]]
    ))


(defonce terminal-states #{"aborted" "failed" "passed"})

;#### utils ###################################################################

(defn get-trial [id]
  (first (jdbc/query (rdbms/get-ds)
                     ["SELECT * FROM trials WHERE id = ?" id])))

(defn issue-description [ex]
  (str (.getMessage ex) " "
       (cond
         (instance? clojure.lang.ExceptionInfo ex)
         (or (-> ex ex-data :object :body)
             (-> ex ex-data))
         :else (str  "\n\n"  (thrown/stringify ex)))))

(defmacro wrap-trial-with-issue-and-throw-again [trial title & body]
  `(try
     ~@body
     (catch Exception e#
       (let [row-data#  {:trial_id (:id ~trial)
                         :title ~title
                         :description (issue-description e#)}]
         (logging/warn ~trial row-data# e#)
         (jdbc/insert! (rdbms/get-ds) "trial_issues" row-data#))
       (throw e#))))


;#### update trial ############################################################

(defn dispatch-update [trial]
  (let [task-id (or (:task_id trial)
                    (:task_id (get-trial (:id trial))))]
    (assert task-id)
    (task/evaluate-and-create-trials {:id task-id})))

(defn- new-state [trial update-params]
  ; prevent executing, pending, etc when state is  aborted or aborting
  (case (:state trial)
    "aborted"  (case (:state update-params)
                 "passed" "passed"
                 "failed" "failed"
                 "aborted")
    "aborting" (case (:state update-params)
                 "passed" "passed"
                 "failed" "aborted"
                 "aborted" "aborted"
                 "aborting")
    (:state update-params)))

(defn- compute-update-params [params id]
  (when-let [trial (get-trial id)]
    (conj {}
          (select-keys params
                       [:error :finished_at :result
                        :started_at :state])
          (when-let [params-scripts (:scripts params)]
            (let [trial (get-trial id)]
              {:scripts (deep-merge (:scripts trial)
                                    params-scripts)}))
          (when-let [new-state (new-state trial params)]
            {:state new-state}))))

(defn update [params]
  (catcher/wrap-with-suppress-and-log-warn
    (let [id (:id params)]
      (try
        (assert id)
        (when-let [update-params (compute-update-params params id)]
          (jdbc/update! (rdbms/get-ds)
                        :trials update-params
                        ["id = ?" id]))
        (catch Exception e
          (jdbc/update! (rdbms/get-ds)
                        :trials
                        {:state "failed"
                         :error (thrown/stringify e)}
                        ["id = ?" id]))
        (finally
          (dispatch-update (select-keys params [:id :task_id])))))))


;#### sql helpers #############################################################

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
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

