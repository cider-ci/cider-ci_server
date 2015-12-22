; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.trials
  (:require
    [cider-ci.dispatcher.task :as task]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
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

(def ^:private permitted-update-keys
  #{:error :result :started_at :finished_at :aborted_by :aborted_at})

(defn- compute-update-params [params id]
  (when-let [trial (get-trial id)]
    (merge (select-keys params permitted-update-keys)
           (when-let [new-state (new-state trial params)]
             {:state new-state}))))

(defn update-trial [params]
  (catcher/wrap-with-suppress-and-log-warn
    (let [id (:id params)]
      (try
        (assert id)
        (when-let [update-params (compute-update-params params id)]
          (when-not (empty? update-params)
            (jdbc/update! (rdbms/get-ds)
                          :trials update-params
                          ["id = ?" id])))
        (catch Exception e
          (jdbc/insert! (rdbms/get-ds)
                        :trial_issues {:trial_id id
                                       :title "Updating the trial failed"
                                       :type "warning"
                                       :description (str (thrown/stringify e)
                                                         "\n\n PARAMS:" params)})
          (jdbc/update! (rdbms/get-ds)
                        :trials {:state "failed" :error (thrown/stringify e)}
                        ["id = ?" id]))
        (finally
          (dispatch-update (select-keys params [:id :task_id])))))))

;(update-trial {:id "82ff77e8-8f45-440b-8505-d0ba6d049354" :state "passed"})

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

;(debug/wrap-with-log-debug #'update-trial)
;(debug/wrap-with-log-debug #'compute-update-params)
;(debug/wrap-with-log-debug #'new-state)
