; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch
  (:require
    [cider-ci.dispatcher.task :as task]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [robert.hooke :as hooke]
    ))


(declare 
  branch-and-commit  
  build-dispatch-data 
  dispatch 
  dispatch-trials 
  executors-to-dispatch-to 
  route-url-for-executor
  to-be-dispatched-trials 
  )



(defonce conf (atom nil))

;### build urls ###############################################################

(defn- get-repository-http-config [executor]
  (if (:server_overwrite executor) 
    executor 
    (:repository_service @conf)))

(defn- get-storage-http-config [executor]
  (if (:server_overwrite executor) 
    executor 
    (:storage_service @conf)))

(defn- git-url [executor repository-id]
  (let [config (get-repository-http-config executor)]
    (http/build-url config (str "/" repository-id "/git"))))

(defn- trial-attachments-url [executor trial-id]
  (let [config (get-storage-http-config executor)]
    (http/build-url config (str "/trial-attachments/" trial-id "/"))))

(defn- tree-attachments-url [executor tree-id]
  (let [config (get-storage-http-config executor)]
    (http/build-url config (str "/tree-attachments/" tree-id "/"))))

(defn- patch-url [executor trial-id]
  (route-url-for-executor 
    executor 
    (str "/trials/" trial-id )))


;### build urls ###############################################################
 
(defn dispatch [trial executor]
  (try
    (with/logging 
      (let [data (build-dispatch-data trial executor)
            protocol (if (:ssl executor) "https" "http")
            url (str protocol "://" (:host executor) 
                     ":" (:port executor) "/execute")]
        (jdbc/update! (rdbms/get-ds) :trials 
                      {:state "dispatching" :executor_id (:id executor)} 
                      ["id = ?" (:id trial)])
        (http/post url {:body (json/write-str data)})))
    (catch Exception e
      (jdbc/update! (rdbms/get-ds) :trials 
                    {:state "pending" :executor_id nil} ["id = ?" (:id trial)])
      false)))

(defn dispatch-trials []
  (doseq [trial (to-be-dispatched-trials)]
    (loop [executors (executors-to-dispatch-to (:id trial))]
      (if-let [executor (first executors)]
        (if-not (dispatch trial executor)
          (recur (rest executors)))))))

(defn executors-to-dispatch-to [trial-id]
  (jdbc/query (rdbms/get-ds)
    ["SELECT executors_with_load.*
     FROM executors_with_load,
     tasks
     INNER JOIN trials on trials.task_id = tasks.id
     WHERE trials.id = ?
     AND (tasks.traits <@ executors_with_load.traits)
     AND executors_with_load.enabled = 't'
     AND (last_ping_at > (now() - interval '1 Minutes'))
     AND (executors_with_load.relative_load < 1)
     ORDER BY executors_with_load.relative_load ASC " trial-id]))

(defn to-be-dispatched-trials []
  (jdbc/query (rdbms/get-ds)
    ["SELECT trials.* FROM trials 
     INNER JOIN tasks ON tasks.id = trials.task_id 
     INNER JOIN executions ON executions.id = tasks.execution_id 
     WHERE trials.state = 'pending'  
     ORDER BY executions.priority DESC, executions.created_at ASC, tasks.priority DESC, tasks.created_at ASC"]
    ))


  
(defn build-dispatch-data [trial executor]
  (let [task (first (jdbc/query (rdbms/get-ds)
                                ["SELECT * FROM tasks WHERE tasks.id = ?" (:task_id trial)]))
        task-spec (task/get-task-spec (:id task))
        execution-id (:execution_id task)
        branch (branch-and-commit execution-id)
        tree-id (:tree_id branch)
        repository-id (:repository_id branch)
        trial-id (:id trial)
        environment-variables (conj (or (:environment_variables task-spec) {})
                                    {:CIDER_CI_EXECUTION_ID execution-id
                                     :CIDER_CI_TASK_ID (:task_id trial)
                                     :CIDER_CI_TRIAL_ID trial-id
                                     :CIDER_CI_TREE_ID (:tree_id branch)})
        data {:trial_attachments (:trial_attachments task-spec)
              :tree_attachments (:tree_attachments task-spec)
              :trial_attachments_url (trial-attachments-url executor trial-id)
              :tree_attachments_url (tree-attachments-url executor tree-id)
              :execution_id execution-id
              :task_id (:task_id trial)
              :trial_id trial-id
              :environment_variables environment-variables
              :git_branch_name (:name branch)
              :git_tree_id (:tree_id branch)
              :git_commit_id (:git_commit_id branch)
              :git_url (git-url executor repository-id)
              :patch_url (patch-url executor trial-id)
              :ports (:ports task-spec)
              :repository_id repository-id
              :scripts (:scripts trial) }]
    data))

(defn branch-and-commit [execution-id] 
  (first (jdbc/query (rdbms/get-ds)
           ["SELECT branches.name, branches.repository_id, 
              commits.tree_id as tree_id,
              commits.id as git_commit_id FROM branches 
            INNER JOIN branches_commits ON branches.id = branches_commits.branch_id 
            INNER JOIN commits ON branches_commits.commit_id = commits.id 
            INNER JOIN executions ON commits.tree_id = executions.tree_id
            WHERE executions.id = ? 
            ORDER BY branches.updated_at DESC" execution-id])))

(defn route-url-for-executor [executor path]
  (let [config (if (:server_overwrite executor) 
                 executor 
                 (:dispatcher_service @conf))]
    (http/build-url config path))) 


;#### dispatch service ########################################################
(daemon/define "dispatch-service" 
  start-dispatch-service 
  stop-dispatch-service 
  1
  (logging/debug "dispatch-service")
  (dispatch-trials))

;#### initialize ##############################################################
(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-dispatch-service))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
