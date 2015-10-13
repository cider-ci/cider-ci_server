; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch.build-data
  (:require
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))


;### URLs #####################################################################

(defn- git-path [repository-id]
  (http/build-service-path :repository (str "/" repository-id "/git")))

(defn- git-url [repository-id]
  ( ->
    (jdbc/query
      (rdbms/get-ds)
      ["SELECT git_url FROM repositories WHERE id = ?" repository-id])
    first
    :git_url))

(defn- trial-attachments-path [trial-id]
  (http/build-service-path :storage  (str "/trial-attachments/" trial-id "/")))

(defn- tree-attachments-path [tree-id]
  (http/build-service-path :storage  (str "/tree-attachments/" tree-id "/")))

(defn- patch-path [executor trial-id]
  (http/build-service-path :dispatcher (str "/trials/" trial-id )))

(defn- add-git-url [data repository-id]
  (conj data
        {:git_path (git-path repository-id)
         :git_url (git-url repository-id) }))


;### templates ################################################################

(defn- templates-data [task-spec]
  (if-let [templates (:templates task-spec)]
    (-> templates convert-to-array)
    []))

;### dispatch data ############################################################

(defn get-branch-and-commit [job-id]
  (first (jdbc/query (rdbms/get-ds)
           ["SELECT branches.name, branches.repository_id,
              commits.tree_id as tree_id,
              commits.id as git_commit_id FROM branches
            INNER JOIN branches_commits ON branches.id = branches_commits.branch_id
            INNER JOIN commits ON branches_commits.commit_id = commits.id
            INNER JOIN jobs ON commits.tree_id = jobs.tree_id
            WHERE jobs.id = ?
            ORDER BY branches.updated_at DESC" job-id])))

(defn build-dispatch-data [trial executor]
  (let [task (first (jdbc/query (rdbms/get-ds)
                                ["SELECT * FROM tasks WHERE tasks.id = ?" (:task_id trial)]))
        task-spec (task/get-task-spec (:id task))
        job-id (:job_id task)
        branch-and-commit (get-branch-and-commit job-id)
        tree-id (:tree_id branch-and-commit)
        repository-id (:repository_id branch-and-commit)
        trial-id (:id trial)
        environment-variables (conj (or (:environment-variables task-spec) {})
                                    {:CIDER_CI_JOB_ID job-id
                                     :CIDER_CI_TASK_ID (:task_id trial)
                                     :CIDER_CI_TRIAL_ID trial-id
                                     :CIDER_CI_TREE_ID (:tree_id branch-and-commit)})
        data {
              :environment-variables environment-variables
              :job_id job-id
              :git_branch_name (:name branch-and-commit)
              :git_commit_id (:git_commit_id branch-and-commit)
              :git-options (or (:git-options task-spec) {})
              :git_tree_id (:tree_id branch-and-commit)
              :patch_path (patch-path executor trial-id)
              :ports (:ports task-spec)
              :repository_id repository-id
              :scripts (:scripts trial)
              :task_id (:task_id trial)
              :templates (templates-data task-spec)
              :tree-attachments (:tree-attachments task-spec)
              :tree-attachments-path (tree-attachments-path tree-id)
              :trial-attachments (:trial-attachments task-spec)
              :trial-attachments-path (trial-attachments-path trial-id)
              :trial_id trial-id
              }]
    (-> data
        (add-git-url repository-id))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
