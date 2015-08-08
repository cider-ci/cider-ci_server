; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.job
  (:require
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))


;#### evaluate and update #####################################################

(defn- get-task-states [job]
  (map :state (jdbc/query
                (rdbms/get-ds)
                ["SELECT state FROM tasks
                 WHERE job_id = ?" (:id job)])))

(defn- update-state-and-fire-if-changed [job-id new-state]
  (when (stateful-entity/update-state
          :jobs job-id new-state {:assert-existence true})
    (messaging/publish "job.updated" {:id job-id :state new-state})))

(defn- get-job [job-id]
  (->> (jdbc/query (rdbms/get-ds)
                   ["SELECT * FROM jobs WHERE id = ?" job-id])
       first))

(defn- evaluate-aborting [job]
  (let [states (get-task-states job)]
    (if-let [new-state (cond
                         (every? #{"passed"} states) "passed"
                         (every? #{"passed" "failed"} states) "aborted"
                         (some #{"pending" "dispatching" "executing"} states) nil
                         :else (throw (ex-info (str "No matching case in "
                                                    'evaluate-aborting)
                                               {:states states :job job})) )]
      (update-state-and-fire-if-changed (:id job) new-state))))

(defn- evaluate-standard [job]
  (let [states (get-task-states job)]
    (if-let [new-state (cond
                         (every? #{"passed"} states) "passed"
                         (every? #{"failed" "passed"} states) "failed"
                         (some #{"executing"} states) "executing"
                         (some #{"pending"} states)"pending"
                         (empty? states) nil
                         :else (throw (ex-info (str "No matching case in "
                                                    'evaluate-standard)
                                               {:states states :job job})))]
      (update-state-and-fire-if-changed (:id job) new-state))))


(defn evaluate-and-update [job-id]
  (catcher/wrap-with-log-warn
    (let [job (get-job job-id)]
      (case (:state job)
        ("aborted" "aborting") (evaluate-aborting job)
        (evaluate-standard job)))))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
