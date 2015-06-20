; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.job
  (:require
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defn- get-task-states [id]
  (map :state (jdbc/query
                (rdbms/get-ds)
                ["SELECT state FROM tasks
                 WHERE job_id = ?" id])))


(defn update-state-and-fire-if-changed [job-id new-state]
  (when (stateful-entity/update-state
          :jobs job-id new-state {:assert-existence true})
    (messaging/publish "job.updated" {:id job-id :state new-state})))

(defn evaluate-and-update [job-id]
  (let [ states (get-task-states job-id)
        update-to #(update-state-and-fire-if-changed job-id %)]
    (cond
      (every? #{"passed"} states) (update-to "passed")
      (every? #{"aborted"} states) (update-to "aborted")
      (every? #{"aborted" "failed" "passed"} states) (update-to "failed")
      (some #{"executing"} states) (update-to "executing")
      (some #{"pending"} states) (update-to "pending")
      (empty? states) false
      :else (throw (IllegalStateException. "NOOOO")))))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
