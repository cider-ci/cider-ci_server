; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.task
  (:require
    [cider-ci.dispatcher.job :as job]
    [cider-ci.dispatcher.result :as result]
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [cider-ci.dispatcher.scripts :refer [create-scripts]]

    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.core.memoize :as core.memoize]
    [clojure.java.jdbc :as jdbc]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]

    ))


;### utils ####################################################################
(defn terminal-states []
  (-> (get-config) :constants :STATES :FINISHED set))

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
                      WHERE task_id = ? ORDER BY created_at ASC" id]))))

(defn- get-job-for-task [task]
  (->> (jdbc/query (rdbms/get-ds)
                   ["SELECT jobs.* FROM jobs
                    JOIN tasks ON tasks.job_id = jobs.id
                    WHERE tasks.id = ? " (:id task)]) first))


;### get task spec data #######################################################

(defn- _get-task-spec-data [id]
  (or (-> (jdbc/query (rdbms/get-ds)
                      ["SELECT data FROM task_specifications WHERE id = ?" id])
          first
          :data)
      (throw (ex-info "Do not cache" {:msg :do-not-cache}))))

(def _get-task-spec-data-memoized
  (core.memoize/lru _get-task-spec-data))

(defn- get-task-spec-data [id]
  (try (_get-task-spec-data-memoized id)
       (catch clojure.lang.ExceptionInfo e
         (if (= (-> e ex-data :msg) :do-not-cache)
           nil
           (throw e)))))

;(get-task-spec-data "2b1b1ac7-bfda-54c6-b1c3-78e54ec27f52")
;(get-task-spec-data-memoized_ "2b1b1ac7-bfda-54c6-b1c3-78e54ec27f52")
;(get-task-spec-data "2b1b1ac7-bfda-54c6-b1c3-78e54ec27f53")


;### re-evaluate  #############################################################

(defn- eval-new-state [task trial-states]
  (let [spec (-> task :task_specification_id get-task-spec-data)]
    (logging/debug 'spec spec)
    (if (= "satisfy-last" (-> spec :aggregate_state))
      (let [state (or (last trial-states) "defective")]
        (case state
          "dispatching" "executing"
          state))
      (cond (empty? trial-states) "defective"
            (some #{"passed"} trial-states) "passed"
            (some #{"executing" "dispatching"} trial-states) "executing"
            (= (last trial-states) "aborted") "aborted"
            (every? #{"defective"} trial-states) "defective"
            (some #{"pending"} trial-states) "pending"
            (some #{"aborting"} trial-states) "aborting"
            (some #{"failed"} trial-states) "failed"
            :else (do (logging/warn 'eval-new-state "Unmatched condition"
                                    {:task task :trial-states trial-states})
                      "defective")))))

(defn- evaluate-trials-and-update
  "Returns a truthy value when the state of the task has changed."
  [task]
  (catcher/with-logging {}
    (let [id (:id task)
          task (get-task id)
          trial-states (get-trial-states task)
          new-state (eval-new-state task trial-states)]
      (result/update-task-and-job-result id)
      (stateful-entity/update-state :tasks id new-state {:assert-existence true}))))



;### create trial #############################################################

(defn create-trial [task params]
  ;TODO: wrap with transaction and retry
  (catcher/with-logging {}
    (let [trial (jdbc/with-db-transaction [tx (rdbms/get-ds)]
                  (let [task-id (:id task)
                        spec (get-task-spec task-id)
                        scripts (:scripts spec)]
                    (let [trial (-> (jdbc/insert! tx :trials
                                                  (merge params
                                                         {:task_id task-id}))
                                    first)]
                      (create-scripts tx trial scripts)
                      trial)))]
      (evaluate-trials-and-update task)
      trial)))

(defn- create-trials [task]
  (let [id (:id task)
        job (get-job-for-task task)
        spec (get-task-spec id)
        states (get-trial-states task)
        finished-count (->> states (filter #((terminal-states) %)) count)
        in-progress-count (- (count states) finished-count)
        create-new-trials-count (min (- (or (:eager_trials spec) 1) in-progress-count)
                                     (- (or (:max_trials spec) 2) (count states)))
        _range (range 0 create-new-trials-count)]
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
        (try
          (create-trial task {})
          (catch Exception e
            (let [row-data  {:job_id (:id job)
                             :title "Error when creating trial and scripts."
                             :description (thrown/stringify e)}]
              (logging/warn row-data)
              (jdbc/insert! (rdbms/get-ds) "job_issues" row-data))))))))


;### eval and create trials ###################################################

(defn evaluate-and-create-trials
  "Evaluate task, evaluate state of trials and adjust state of task.
  Create trials according to max_trials and eager_trials properties
  if task is not in terminal state."
  [task_id]
  (locking (str "evaluate-and-create-trials_for_" task_id)
    (let [task (get-task task_id)]
      (create-trials task)
      (evaluate-trials-and-update task))))


;### initialize ###############################################################

(defn evaluate-task-eval-notifications []
  (I>> identity-with-logging
       "SELECT * FROM task_eval_notifications ORDER BY created_at ASC, task_id ASC LIMIT 100"
       (jdbc/query (rdbms/get-ds))
       (map (fn [row]
              (future
                (catcher/snatch
                  {} (evaluate-and-create-trials (:task_id row)))
                row)))
       (map deref)
       (map :id)
       (map #(jdbc/delete! (rdbms/get-ds) :task_eval_notifications ["id = ?" %]))
       doall))

(defdaemon "evaluate-task-eval-notifications"
  0.25 (evaluate-task-eval-notifications))

(defn initialize []
  (catcher/with-logging {}
    (start-evaluate-task-eval-notifications)))

;(messaging/publish "task.create-trials" {:id "de10e33c-c13f-5aba-94aa-db1dca1e5932"})

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'evaluate-and-create-trials)
;(debug/wrap-with-log-debug #'eval-new-state)
;(debug/re-apply-last-argument #'get-trial-states)
