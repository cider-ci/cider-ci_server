; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.task
  (:require
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.dispatcher.stateful-entity :as stateful-entity]
    [cider-ci.dispatcher.execution :as execution]
    [cider-ci.dispatcher.result :as result]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.rdbms.conversion :as rdbms.conversion]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


;### utils ####################################################################
(defonce terminal-states #{"aborted" "failed" "passed"})

(defn sort-map-by-order-value [mp]
  (into {} (sort-by 
             #(let [[k v] %] 
                (or (:order v) 0))   
             mp)))

;(sort-map-by-order-value {:z {:order 2}  :x {:order 1}})

(defn convert-scripts-to-array [scripts-map]
  (sort-by #(or (:order %) 0) 
           (for [[script-name properties] scripts-map]
             (conj properties {:name script-name})))
  ;(convert-scripts-to-array {:main {:order 5 :body "main body" } :prepare {:order 0 :body "prepare"}}) 
  )

(defn get-task [id]
  (first (jdbc/query (rdbms/get-ds) 
                                ["SELECT * FROM tasks
                                 WHERE id = ?::UUID" id])))
(defn get-task-spec [task-id]
  (let [ task_specs (jdbc/query (rdbms/get-ds) 
                                ["SELECT task_specs.data FROM task_specs
                                 JOIN tasks ON tasks.task_spec_id = task_specs.id
                                 WHERE tasks.id = ?::UUID" task-id])
        spec (clojure.walk/keywordize-keys (:data (first task_specs)))]
    spec))

(defn- get-trial-states [task]
  (let [id (rdbms.conversion/convert-to-uuid (:id task))]
    (map :state 
         (jdbc/query (rdbms/get-ds) 
                     ["SELECT state FROM trials 
                      WHERE task_id = ?::UUID" id]))))


;### create trial #############################################################
(defn create-trial [task]
  (let [task-id (rdbms.conversion/convert-to-uuid (:id task))
        spec (get-task-spec task-id)]
    (with/logging
      (jdbc/insert! (rdbms/get-ds) :trials
                    {:scripts  (rdbms.conversion/convert-to-json 
                                 (sort-map-by-order-value (:scripts spec)))
                     :task_id task-id})))
  ;(create-trial "d0e04847-ef9c-53ec-a009-18a02a7b8f81")
  )

(defn- create-trials [task]
  (let [id (rdbms.conversion/convert-to-uuid (:id task))
        spec (get-task-spec id)
        states (get-trial-states task) 
        finished-count (->> states (filter #(terminal-states %)) count)
        in-progress-count (- (count states) finished-count)
        create-new-trials-count (min (- (or (:eager_trials spec) 1) in-progress-count)
                                     (- (or (:auto_trials spec) 2) (count states)))
        _range (range 0 create-new-trials-count)
        ]
    (logging/debug "CREATE-TRIALS" 
                   {:id id :spec spec :states states 
                    :finished-count finished-count
                    :in-progress-count in-progress-count 
                    :create-new-trials-count create-new-trials-count
                    :_range _range
                    })
    (when-not (some #{"passed"} states)
      (logging/debug "seqing and creating trials" )
      (doseq [_ _range]
        (create-trial task)))))


;### re-evaluate  #############################################################

(defn- evaluate-trials-and-update
  [task]
  (with/logging
    (let [id (rdbms.conversion/convert-to-uuid (:id task))
          states (get-trial-states task)
          update-to #(stateful-entity/update-state 
                       :tasks id % {:assert-existence true})]
      (result/update-task-and-execution-result id)
      (cond 
        (some #{"passed"} states) (update-to "passed")
        (every? #{"aborted"} states) (update-to "aborted")
        (every? #{"failed" "aborted"} states) (update-to "failed")
        (some #{"executing"} states) (update-to "executing")
        (some #{"pending" "dispatching"} states) (update-to "pending")
        (empty? states) false
        :else (throw (IllegalStateException. "NOOOO"))
        ))))

(defn evaluate-and-create-trials
  "Evaluate task, evaluate state of trials and adjust state of task.
  Send \"task.state-changed\" message if state changed.
  Create trials according to auto_trials and eager_trials properties
  if task is not in terminal state. The argument task must be a map 
  including an :id key" 
  [task]
  (create-trials task)
  (when (evaluate-trials-and-update task)
    (messaging/publish "task.state-changed" task)
    (execution/evaluate-and-update (:execution_id (get-task (:id task)))))
  ;(evaluate-and-create-trials {:id "d0e04847-ef9c-53ec-a009-18a02a7b8f81"})
  ;(evaluate-and-create-trials {:id "6946714b-9bbd-5c78-9304-878bd2cf6049"})
  )


;### initialize ###############################################################
(defn initialize []
  (with/logging
    (messaging/listen "task.create-trials" 
                      #'create-trials
                      "task.create-trials")

    (messaging/listen "task.create-trial" 
                      #'create-trial
                      "task.create-trial"))
  )

;(messaging/publish "task.create-trials" {:id "de10e33c-c13f-5aba-94aa-db1dca1e5932"})

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
