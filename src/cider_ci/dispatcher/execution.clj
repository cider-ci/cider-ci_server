; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.execution
  (:require
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defn- get-task-states [id]
  (map :state (jdbc/query 
                (rdbms/get-ds)
                ["SELECT state FROM tasks
                 WHERE execution_id = ?::UUID" id])))
  
(defn evaluate-and-update [execution-id]
  (let [ states (get-task-states execution-id)
        update-to #(stateful-entity/update-state 
                     :executions execution-id % {:assert-existence true})]
    (cond 
      (every? #{"success"} states) (update-to "success")
      (every? #{"aborted"} states) (update-to "aborted")
      (every? #{"aborted" "failed" "success"} states) (update-to "failed")
      (some #{"executing"} states) (update-to "executing")
      (some #{"pending"} states) (update-to "pending")
      (empty? states) false
      :else (throw (IllegalStateException. "NOOOO"))
      )))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
