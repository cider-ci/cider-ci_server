(ns cider-ci.server.builder.evaluation.tasks
  (:require
    [cider-ci.server.builder.util :refer [task! update-state]]

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

(def task-specification!
  (clojure.core.memoize/lu
    (fn [task-id]
      (or (->> [(str "SELECT data FROM task_specifications "
                     " INNER JOIN tasks"
                     "   ON tasks.task_specification_id = task_specifications.id"
                     " WHERE tasks.id = ?") task-id]
               (jdbc/query (rdbms/get-ds))
               first)
          (throw (ex-info "task-specification not found" {:task-id task-id}))))))


;#### evaluate and update #####################################################

(defn- trial-states [task-id tx]
  (->> [(str "SELECT state FROM trials WHERE task_id = ? "
             " ORDER BY created_at ASC" ) task-id]
       (jdbc/query tx)
       (map :state)))

(defn- new-state [task-id tx]
  (let [spec (-> task-id task-specification! :data)
        trial-states (trial-states task-id tx)]
    (logging/debug 'spec spec)
    (if (= "satisfy-last" (-> spec :aggregate_state))
      (let [state (or (last trial-states) "defective")]
        (case state
          "dispatching" "executing"
          state))
      (cond (empty? trial-states) "defective"
            (some #{"executing" "dispatching"} trial-states) "executing"
            (some #{"passed"} trial-states) "passed"
            (= (last trial-states) "aborted") "aborted"
            (every? #{"defective"} trial-states) "defective"
            (some #{"pending"} trial-states) "pending"
            (some #{"aborting"} trial-states) "aborting"
            (some #{"failed"} trial-states) "failed"
            :else (do (logging/warn 'eval-new-state "Unmatched condition"
                                    {:task-id task-id :trial-states trial-states})
                      "defective")))))

(defn evaluate-and-update [row]
  (let [task-id (:task_id row)]
    (locking (str "evaluate-and-update task_id: " task-id)
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (update-state :tasks task-id (new-state task-id tx) tx)))))


;### worker ###################################################################

(def evaluate-pending-task-evaluations
  (pending-rows/build-worker
    "pending_task_evaluations"
    evaluate-and-update))

(defdaemon "evaluate-pending-task-evaluations"
  0.05 (evaluate-pending-task-evaluations))


;### initialize ###############################################################

(defn initialize []
  (catcher/with-logging {}
    (start-evaluate-pending-task-evaluations)))
