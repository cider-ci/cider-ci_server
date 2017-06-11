(ns cider-ci.server.builder.evaluation.jobs
  (:require
    [cider-ci.server.builder.util :refer [job! update-state]]

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


;#### evaluate and update #####################################################

(defn- get-task-states [job tx]
  (->> ["SELECT state FROM tasks WHERE job_id = ?" (:id job)]
       (jdbc/query tx)
       (map :state)))

(defn- evalute-new-state [job task-states]
  (case (:state job)
    "aborting" (cond (every? #{"passed"} task-states) "passed"
                     (some #{"aborting"} task-states) "aborting"
                     (every? #{"passed" "failed" "aborted"} task-states) "aborted"
                     :else "aborting")
    (cond
      (every? #{"passed"} task-states) "passed"
      (some #{"executing"} task-states) "executing"
      (some #{"pending"} task-states) "pending"
      (some #{"aborted"} task-states) "aborted"
      (some #{"defective"} task-states) "defective"
      (some #{"failed"} task-states) "failed"
      :else (:state job))))

(defn evaluate-and-update [row]
  (let [job-id (:job_id row)]
    (locking (str "evaluate-and-update job_id: " job-id)
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (let [job (job! job-id tx)
              task-states (get-task-states job tx)
              new-state (evalute-new-state job task-states)]
          (update-state :jobs job-id new-state tx))))))


;### worker ###################################################################

(def evaluate-pending-job-evaluations
  (pending-rows/build-worker
    "pending_job_evaluations"
    evaluate-and-update))

(defdaemon "evaluate-pending-job-evaluations"
  0.05 (evaluate-pending-job-evaluations))


;### initialize ###############################################################

(defn initialize []
  (catcher/with-logging {}
    (start-evaluate-pending-job-evaluations)))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)

