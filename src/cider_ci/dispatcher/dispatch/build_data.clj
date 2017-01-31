; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch.build-data
  (:require
    [cider-ci.dispatcher.scripts :refer [get-scripts]]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clojure.java.jdbc :as jdbc]
    [clojure.string :refer [blank?]]
    [honeysql.sql :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


;### URLs #####################################################################

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
  (str "/cider-ci/dispatcher/trials/" trial-id ))

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
        commit-id (:git_commit_id branch-and-commit)
        environment-variables (conj (or (:environment_variables task-spec) {})
                                    {:CIDER_CI_JOB_ID job-id
                                     :CIDER_CI_TASK_ID (:task_id trial)
                                     :CIDER_CI_TRIAL_ID trial-id
                                     :CIDER_CI_TREE_ID (:tree_id branch-and-commit)})]
    (merge (select-keys trial [:token :task_id])
           {:environment_variables environment-variables
            :git_options (or (:git_options task-spec) {})
            :git_branch_name (:name branch-and-commit)
            :git_commit_id commit-id
            :git_tree_id (:tree_id branch-and-commit)
            :git_url (git-url repository-id)
            :git-proxies {}
            :job_id job-id
            :patch_path (patch-path executor trial-id)
            :ports (:ports task-spec)
            :repository_id repository-id
            :scripts (get-scripts trial)
            :templates (templates-data task-spec)
            :tree_attachments (:tree_attachments task-spec)
            :tree_attachments_path (tree-attachments-path tree-id)
            :trial_attachments (:trial_attachments task-spec)
            :trial_attachments_path (trial-attachments-path trial-id)
            :trial_id trial-id
            })))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
