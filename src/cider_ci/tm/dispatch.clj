; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.dispatch
  (:require
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [robert.hooke :as hooke]
    [cider-ci.utils.with :as with]
    [cider-ci.rdbms.json]
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


;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom nil))
 
(defn dispatch [trial executor]
  (try
    (logging/debug "dispatching: " trial executor)
    (with/logging 
      (let [data (build-dispatch-data trial executor)
            protocol (if (:ssl executor) "https" "http")
            url (str protocol "://" (:host executor) 
                     ":" (:port executor) "/execute")]
        (jdbc/update! (:ds @conf) :trials 
                      {:state "dispatching" :executor_id (:id executor)} 
                      ["id = ?" (:id trial)])
        (http-client/post
          url
          {:insecure? true
           :content-type :json
           :accept :json 
           :body (json/write-str data)
           :socket-timeout 1000  
           :conn-timeout 1000 })))
    (catch Exception e
      (jdbc/update! (:ds @conf) :trials 
                    {:state "pending" :executor_id nil} ["id = ?" (:id trial)])
      false)))

(defn dispatch-trials []
  (doseq [trial (to-be-dispatched-trials)]
    (loop [executors (executors-to-dispatch-to (:id trial))]
      (if-let [executor (first executors)]
        (if-not (dispatch trial executor)
          (recur (rest executors)))))))

(defn executors-to-dispatch-to [trial-id]
  (jdbc/query (:ds @conf)
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
  (jdbc/query (:ds @conf)
    ["SELECT trials.* FROM trials 
     INNER JOIN tasks ON tasks.id = trials.task_id 
     INNER JOIN executions ON executions.id = tasks.execution_id 
     WHERE trials.state = 'pending'  
     ORDER BY executions.priority DESC, executions.created_at ASC, tasks.priority DESC, tasks.created_at ASC"]
    ))

(defn git-url [executor repository-id]
  (str (:repositories_server @conf)
       "/repositories/" repository-id "/git"))

(defn attachments-url [executor trial-id]
  (route-url-for-executor 
    executor 
    (str "/executors_api_v1/trials/" trial-id "/attachments/")))

(defn patch-url [executor trial-id]
  (route-url-for-executor 
    executor 
    (str "/executors_api_v1/trials/" trial-id )))
  

(defn submodules-dispatch-data [submodules executor]
  (map 
    (fn [submodule]
      {:git_commit_id (:commit_id submodule)
       :repository_id (:repository_id submodule)
       :git_url (git-url executor (:repository_id submodule))
       :subpath_segments (:path submodule)
       })
    submodules))

(defn get-submodule-definitions [id]
  (logging/warn "TODO" get-submodule-definitions)
  [])

(defn build-dispatch-data [trial executor]
  (let [task (first (jdbc/query (:ds @conf)
                      ["SELECT * FROM tasks WHERE tasks.id = ?" (:task_id trial)]))
        execution-id (:execution_id task)
        branch (branch-and-commit execution-id)
        repository-id (:repository_id branch)
        submodules (get-submodule-definitions (:git_commit_id branch))
        trial-id (:id trial)
        task-data (clojure.walk/keywordize-keys (:data task))
        environment-variables (conj (or (:environment_variables task-data) {})
                                    {:cider_ci_execution_id execution-id
                                     :cider_ci_task_id (:task_id trial)
                                     :cider_ci_trial_id trial-id})
        data {:attachments (:attachments task-data)
              :attachments_url (attachments-url executor trial-id)
              :execution_id execution-id
              :task_id (:task_id trial)
              :trial_id trial-id
              :environment_variables environment-variables
              :git_branch_name (:name branch)
              :git_commit_id (:git_commit_id branch)
              :git_url (git-url executor repository-id)
              :git_submodules (submodules-dispatch-data submodules executor)
              :patch_url (patch-url executor trial-id)
              :ports (:ports task-data)
              :repository_id repository-id
              :scripts (:scripts trial) }]
    (logging/debug data)
    data
    ))


(defn branch-and-commit [execution-id] 
  (first (jdbc/query (:ds @conf)
           ["SELECT branches.name, branches.repository_id, commits.id as git_commit_id FROM branches 
            INNER JOIN branches_commits ON branches.id = branches_commits.branch_id 
            INNER JOIN commits ON branches_commits.commit_id = commits.id 
            INNER JOIN executions ON commits.tree_id = executions.tree_id
            WHERE executions.id = ? 
            ORDER BY branches.updated_at DESC" execution-id])))

(defn route-url-for-executor [executor path]
  (let [config (if  (:server_overwrite executor) executor (:http_server @conf))
        protocol (if (or (:server_ssl config) (:ssl config)) "https" "http")
        host (or (:server_host config) (:host config))
        port (or (:server_port config) (:port config))
        context (:context config)
        ]
    (str protocol "://" host  ":" port context path)))


;#### daemon ##################################################################

(def done (atom false))

(defn start []
  (logging/info "tm started")
  (reset! done false)
  (future 
    (loop []
      (Thread/sleep 1000)
      (when-not @done
        (with/logging-and-suppress
          (dispatch-trials))
        (recur)))))

(defn stop []
  (logging/info "tm stoped")
  (reset! done true))

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start))


; ################################################

(defn logit [f & args]
  (logging/debug f " ARGS: " args)
  (let [res (apply f args)]
    (logging/debug f " RESULT: " res)
    res ))


;(hooke/add-hook #'gsm/submodules-for-commit #'logit)
;(hooke/add-hook #'executors-to-dispatch-to #'logit)
;(hooke/add-hook #'build-dispatch-data #'logit)
;(hooke/add-hook #'route-url-for-executor #'logit)


