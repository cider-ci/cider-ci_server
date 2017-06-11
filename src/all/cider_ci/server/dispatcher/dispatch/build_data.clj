; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.dispatcher.dispatch.build-data
  (:require
    [cider-ci.server.dispatcher.scripts :refer [get-scripts]]
    [cider-ci.server.dispatcher.task :as task]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clojure.java.jdbc :as jdbc]
    [clojure.string :refer [blank?]]
    [honeysql.core :as sql]


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
  (str "/cider-ci/storage/trial-attachments/" trial-id "/"))

(defn- tree-attachments-path [tree-id]
  (str "/cider-ci/storage/tree-attachments/" tree-id "/"))

(defn- patch-path [executor trial-id]
  (str "/cider-ci/dispatcher/trials/" trial-id ))

;### templates ################################################################

(defn- templates-data [task-spec]
  (if-let [templates (:templates task-spec)]
    (-> templates convert-to-array)
    []))

;### dispatch data ############################################################

(def names_of_branches_sub
  (-> (sql/select
        :tree_id
        [(sql/raw "json_agg(branches.name)::text") :names_of_branches])
      (sql/from :commits)
      (honeysql.helpers/merge-left-join
        :branches [:= :commits.id :branches.current_commit_id])
      (honeysql.helpers/group :tree_id)))

(def dispatch-data-base-query
  (-> (sql/select
        [:branches.name :branch_name]
        [:commits.id :commit_id]
        [:jobs.tree_id :tree_id]
        [:jobs.trigger_event :job_trigger_event]
        [:repositories.git_url :git_url]
        [:repositories.id :repository_id]
        [:tasks.id :task_id]
        [:tasks.job_id :job_id]
        [:branch_heads.names_of_branches :current_heads])
      (sql/from :tasks)
      (sql/merge-join :jobs [:= :jobs.id :tasks.job_id])
      (sql/merge-join :commits [:= :commits.tree_id :jobs.tree_id])
      (sql/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
      (sql/merge-join :branches [:= :branches.id :branches_commits.branch_id])
      (sql/merge-join :repositories [:= :repositories.id :branches.repository_id])
      (sql/merge-join [names_of_branches_sub :branch_heads] [:= :branch_heads.tree_id :jobs.tree_id])
      (sql/order-by [:commits.committer_date :desc])
      (sql/limit 1)))

(defn env-vars [trial-id data task-spec]
  (conj (or (:environment_variables task-spec) {})
        {:CIDER_CI_JOB_ID (:job_id data)
         :CIDER_CI_TASK_ID (:task_id data)
         :CIDER_CI_TRIAL_ID trial-id
         :CIDER_CI_TREE_ID (:tree_id data)
         :CIDER_CI_CURRENT_BRANCH_HEADS (:current_heads data)}))

(defn build-dispatch-data [trial executor]
  (let [task-id (:task_id trial)
        data (->> (-> dispatch-data-base-query
                      (sql/merge-where [:= :tasks.id task-id])
                      sql/format)
                  (jdbc/query (rdbms/get-ds)) first)
        task-spec (task/get-task-spec (:task_id data))
        tree-id (:tree_id data)
        trial-id (:id trial)]
    {:environment_variables (env-vars trial-id data task-spec)
     :git-proxies {}
     :git_branch_name (:branch_name data)
     :git_commit_id (:commit_id data)
     :git_options (or (:git_options task-spec) {})
     :git_tree_id tree-id
     :git_url (:git_url data)
     :job_id (:job_id data)
     :patch_path (patch-path executor trial-id)
     :ports (:ports task-spec)
     :repository_id (:repository_id data)
     :scripts (get-scripts trial)
     :task_id task-id
     :templates (templates-data task-spec)
     :token (:token trial)
     :tree_attachments (:tree_attachments task-spec)
     :tree_attachments_path (tree-attachments-path tree-id)
     :trial_attachments (:trial_attachments task-spec)
     :trial_attachments_path (trial-attachments-path trial-id)
     :trial_id trial-id }))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
