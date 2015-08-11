; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.task
  (:require
    [cider-ci.dispatcher.job :as job]
    [cider-ci.dispatcher.result :as result]
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


;### utils ####################################################################
(defonce terminal-states #{"aborted" "failed" "passed"})

(defn get-task [id]
  (first (jdbc/query (rdbms/get-ds)
                                ["SELECT * FROM tasks
                                 WHERE id = ?" id])))
(defn get-task-spec [task-id]
  (let [ task_specifications (jdbc/query (rdbms/get-ds)
                                ["SELECT task_specifications.data FROM task_specifications
                                 JOIN tasks ON tasks.task_specification_id = task_specifications.id
                                 WHERE tasks.id = ?" task-id])
        spec (clojure.walk/keywordize-keys (:data (first task_specifications)))]
    spec))

(defn- get-trial-states [task]
  (let [id (:id task)]
    (map :state
         (jdbc/query (rdbms/get-ds)
                     ["SELECT state FROM trials
                      WHERE task_id = ?" id]))))

(defn- get-job-for-task [task]
  (->> (jdbc/query (rdbms/get-ds)
                   ["SELECT jobs.* FROM jobs
                    JOIN tasks ON tasks.job_id = jobs.id
                    WHERE tasks.id = ? " (:id task)]) first))


;### re-evaluate  #############################################################

(defn- eval-new-state [task trial-states]
  (cond (some #{"passed"} trial-states) "passed"
        (every? #{"aborted"} trial-states ) "aborted"
        (every? #{"failed" "aborted"} trial-states) "failed"
        (some #{"executing"} trial-states) "executing"
        (some #{"pending"} trial-states) "pending"
        (some #{"aborting"} trial-states) "aborting"
        (every? #{"aborted"} trial-states) "aborted"
        :else (do (logging/warn 'eval-new-state "Unmatched condition"
                                {:task task :trial-states trial-states})
                "failed")))

(defn- evaluate-trials-and-update
  "Returns a truthy value when the state of the task has changed."
  [task]
  (catcher/wrap-with-log-error
    (let [id (:id task)
          task (get-task id)
          trial-states (get-trial-states task)
          new-state (eval-new-state task trial-states)]
      (result/update-task-and-job-result id)
      (stateful-entity/update-state :tasks id new-state {:assert-existence true}))))


;### create trial #############################################################

(defn create-trial [task]
  (let [task-id (:id task)
        spec (get-task-spec task-id)
        scripts (:scripts spec) ]
    (catcher/wrap-with-log-error
      (let [trial (-> (jdbc/insert! (rdbms/get-ds) :trials
                                    {:scripts scripts
                                     :task_id task-id})
                      first)]
        (evaluate-trials-and-update task)
        trial))))

(defn- create-trials [task]
  (let [id (:id task)
        job (get-job-for-task task)
        spec (get-task-spec id)
        states (get-trial-states task)
        finished-count (->> states (filter #(terminal-states %)) count)
        in-progress-count (- (count states) finished-count)
        create-new-trials-count (min (- (or (:eager-trials spec) 1) in-progress-count)
                                     (- (or (:max-auto-trials spec) 2) (count states)))
        _range (range 0 create-new-trials-count)
        ]
    (logging/debug "CREATE-TRIALS"
                   {:id id :spec spec :states states
                    :finished-count finished-count
                    :in-progress-count in-progress-count
                    :create-new-trials-count create-new-trials-count
                    :_range _range
                    })
    (when-not (or (some #{"passed"} states)
                  (some #{(:state job)} ["aborted" "aborting"]))
      (logging/debug "seqing and creating trials" )
      (doseq [_ _range]
        (create-trial task)))))


;### eval and create trials ###################################################

(defn evaluate-and-create-trials
  "Evaluate task, evaluate state of trials and adjust state of task.
  Send \"task.state-changed\" message if state changed.
  Create trials according to max-auto-trials and eager-trials properties
  if task is not in terminal state. The argument task must be a map
  including an :id key"
  [task]
  (create-trials task)
  (when (evaluate-trials-and-update task)
    (messaging/publish "task.state-changed" task)
    (job/evaluate-and-update (:job_id (get-task (:id task)))))
  ;(evaluate-and-create-trials {:id "d0e04847-ef9c-53ec-a009-18a02a7b8f81"})
  ;(evaluate-and-create-trials {:id "6946714b-9bbd-5c78-9304-878bd2cf6049"})
  )


;### initialize ###############################################################

(defn initialize []
  (catcher/wrap-with-log-error
    (messaging/listen "task.create-trials"
                      #'create-trials
                      "task.create-trials")))

;(messaging/publish "task.create-trials" {:id "de10e33c-c13f-5aba-94aa-db1dca1e5932"})

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
