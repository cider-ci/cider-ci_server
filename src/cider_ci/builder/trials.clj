(ns cider-ci.builder.trials
  (:require

    [cider-ci.builder.scripts :as scripts]
    [cider-ci.builder.util :refer [job!]]


    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.pending-rows :as pending-rows]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as  logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

;### helpers ##################################################################

(defn terminal-states []
  (-> (get-config) :constants :STATES :FINISHED set))

(defn get-task!
  ([id]
   (get-task! id (rdbms/get-ds)))
  ([id tx]
   (or (->> ["SELECT * FROM tasks WHERE id = ?" id]
            (jdbc/query tx) first)
       (throw (ex-info "Task not found" {:id id})))))

(defn get-task-spec!
  ([task-id]
   (get-task-spec! task-id (rdbms/get-ds)))
  ([task-id tx]
   (or (->> ["SELECT task_specifications.data FROM task_specifications
             JOIN tasks ON tasks.task_specification_id = task_specifications.id
             WHERE tasks.id = ?" task-id]
            (jdbc/query tx)
            first
            :data
            clojure.walk/keywordize-keys)
       (throw (ex-info "Task spec not found" {:task-id task-id})))))

(defn- get-trial-states
  ([task-id]
   (get-trial-states task-id (rdbms/get-ds)))
  ([task-id tx]
   (->> ["SELECT state FROM trials
         WHERE task_id = ? ORDER BY created_at ASC" task-id]
        (jdbc/query tx)
        (map :state))))


;### create-trials ############################################################

(defn- create-trial [task-id task-spec tx]
  (let [trial (->> {:task_id task-id}
                   (jdbc/insert! tx :trials )
                   first)]
    (scripts/create-scripts tx trial (:scripts task-spec))
    trial))

(defn create-trials [task-id options]
  (locking (str "create-trials_for_" task-id)
    (jdbc/with-db-transaction [tx (or (:tx options) (rdbms/get-ds))]
      (let [task (or (:task options) (get-task! task-id tx))
            job (or (:job options) (job! (:job_id task) tx))
            spec (or (:job-spec options) (get-task-spec! (:id task) tx))
            states (get-trial-states task-id)
            finished-count (->> states (filter #((terminal-states) %)) count)
            in-progress-count (- (count states) finished-count)
            create-new-trials-count (min (- (or (:eager_trials spec) 1) in-progress-count)
                                         (- (or (:max_trials spec) 2) (count states)))
            _range (range 0 create-new-trials-count)]
        (logging/debug "CREATE-TRIALS"
                       {:spec spec :states states
                        :finished-count finished-count
                        :in-progress-count in-progress-count
                        :create-new-trials-count create-new-trials-count
                        :_range _range })
        (when-not (or (some #{"passed"} states)
                      (= (last states) "aborted")
                      (some #{(:state job)} ["aborted" "aborting"]))
          (doseq [_ _range]
            (create-trial task-id spec tx)))))))


;### worker ###################################################################

(defn- evaluate-row [row]
  (try (catcher/with-logging {}
         (create-trials (:task_id row) {}))
       (catch Exception _
         (jdbc/update!
           (rdbms/get-ds) :tasks {:state "defective"}
           ["tasks.id = ?" (:task_id row)]))))

(def evaluate-pending-create-trials-evaluations
  (pending-rows/build-worker
    "pending_create_trials_evaluations"
    evaluate-row))

(defdaemon "evaluate-pending-create-trials-evaluations"
  0.05 (evaluate-pending-create-trials-evaluations))


;### initialize ###############################################################

(defn initialize []
  (catcher/with-logging {}
    (start-evaluate-pending-create-trials-evaluations)))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
