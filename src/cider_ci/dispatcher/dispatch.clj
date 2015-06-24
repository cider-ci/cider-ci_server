; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch
  (:require
    [cider-ci.dispatcher.executor :as executor-utils]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.trial :as trial-utils]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    [robert.hooke :as hooke]
    ))


;### build urls ###############################################################

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

(defn add-git-url [data repository-id]
  (conj data
        {:git_path (git-path repository-id)
         :git_url (git-url repository-id) }))

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
              :tree-attachments (:tree-attachments task-spec)
              :tree-attachments-path (tree-attachments-path tree-id)
              :trial-attachments (:trial-attachments task-spec)
              :trial-attachments-path (trial-attachments-path trial-id)
              :trial_id trial-id
              }]
    (-> data
        (add-git-url repository-id))))


;### dispatch #################################################################

(defn choose-executor-to-dispatch-to [trial]
  (->> (-> (hh/select :executors_with_load.*)
           (hh/from :trials)
           (hh/where [:= :trials.id (:id trial)])
           (hh/merge-join :tasks [:= :tasks.id :trials.task_id])
           (hh/merge-join :executors_with_load (hc/raw "(tasks.traits <@ executors_with_load.traits)"))
           (hh/merge-where (hc/raw "(last_ping_at > (now() - interval '1 Minutes'))"))
           (hh/merge-where [:= :enabled true])
           (hh/merge-where [:< :relative_load 1])
           (hh/merge-join :jobs [:= :tasks.job_id :jobs.id])
           (hh/merge-join :commits [:= :jobs.tree_id :commits.tree_id])
           (hh/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
           (hh/merge-join :branches [:= :branches_commits.branch_id :branches.id])
           (hh/merge-join :repositories [:= :branches.repository_id :repositories.id])
           (hh/merge-join [:or
                           (hc/raw "(executors_with_load.accepted_repositories = '{}')")
                           (hc/raw " repositories.git_url = ANY(executors_with_load.accepted_repositories) ")])
           hc/format)
       (jdbc/query (rdbms/get-ds))
       (map (fn [e] (repeat (- (:max_load e) (:current_load e)) e)))
       flatten rand-nth))

(def ^:private available-executor-exists-query
  (->
    (hh/select 1)
    (hh/from :executors_with_load)
    (hh/merge-where [:< :relative_load 1])
    (hh/merge-where [:= :enabled true])
    (hh/merge-where (hc/raw "(tasks.traits <@ executors_with_load.traits)"))
    (hh/merge-where (hc/raw "(last_ping_at > (now() - interval '1 Minutes'))"))))

(def ^:private join-trial-to-repo
  (-> (hh/merge-join :tasks [:= :trials.task_id :tasks.id])
      (hh/merge-join :jobs [:= :tasks.job_id :jobs.id])
      (hh/merge-join :commits [:= :jobs.tree_id :commits.tree_id])
      (hh/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
      (hh/merge-join :branches [:= :branches_commits.branch_id :branches.id])
      (hh/merge-join :repositories [:= :branches.repository_id :repositories.id])))


(def ^:private executor-with-accepted-repositories-part-query
  (-> join-trial-to-repo
      (hh/select 1)
      (hh/from :executors_with_load)
      (hh/merge-where (hc/raw " repositories.git_url = ANY(executors_with_load.accepted_repositories) "))))

(def ^:private available-executor-exists
  [:or [:exists (-> available-executor-exists-query
                    (hh/merge-where (hc/raw "(executors_with_load.accepted_repositories = '{}')")))]
   [:exists (merge
              available-executor-exists-query
              executor-with-accepted-repositories-part-query)]])

(def ^:private without-or-with-available-global-resource
  [ "NOT EXISTS" (-> (hh/select 1)
                     (hh/from [:trials :active_trials])
                     (hh/merge-join [:tasks :active_tasks] [:= :active_tasks.id :active_trials.task_id])
                     (hh/merge-where [:in :active_trials.state  ["executing","dispatching"]])
                     (hh/merge-where (hc/raw (str "active_tasks.exclusive_global_resources "
                                                  "&& tasks.exclusive_global_resources"))))])

; TODO use
(def ^:private is-attached-to-a-repo
  [:exists
   (merge
     (hh/select 1)
     join-trial-to-repo
     )])

(defn get-next-trial-to-be-dispatched []
  (-> (-> (hh/select :trials.*)
          (hh/from :trials)
          (hh/merge-where [:= :trials.state "pending"])
          (hh/merge-where available-executor-exists)
          (hh/merge-where without-or-with-available-global-resource)
          ;(hh/merge-where is-attached-to-a-repo)
          (hh/merge-join :tasks [:= :trials.task_id :tasks.id])
          (hh/merge-join :jobs [:= :tasks.job_id :jobs.id])
          (hh/merge-join :commits [:= :jobs.tree_id :commits.tree_id])
          (hh/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
          (hh/merge-join :branches [:= :branches_commits.branch_id :branches.id])
          (hh/merge-join :repositories [:= :branches.repository_id :repositories.id])
          (hh/order-by [:jobs.priority :desc]
                       [:jobs.created_at :asc]
                       [:tasks.priority :desc]
                       [:tasks.created_at :asc]
                       [:trials.created_at :asc])
          (hh/limit 1)
          hc/format)
      (#(jdbc/query (rdbms/get-ds) %))
      first))

(defn- issues-count [trial]
  (-> (jdbc/query (rdbms/get-ds)
                  ["SELECT count(*) FROM trial_issues WHERE trial_id = ? " (:id trial)] )
      first :count))

(defn post-trial [url basic-auth-pw data]
  (catcher/wrap-with-log-warn
    (http-client/post url
                      {:content-type :json
                       :body (json/write-str data)
                       :insecure? true
                       :basic-auth ["dispatcher" basic-auth-pw ]})))

(defn dispatch [trial executor]
  (try
    (trial-utils/wrap-trial-with-issue-and-throw-again
      trial  "Error during dispatch"
      (let [data (build-dispatch-data trial executor)
            url (str (:base_url executor)  "/execute")
            basic-auth-pw (executor-utils/http-basic-password executor)]
        (jdbc/update! (rdbms/get-ds) :trials
                      {:state "dispatching" :executor_id (:id executor)}
                      ["id = ?" (:id trial)])
        (post-trial url basic-auth-pw data)))
    (catch Exception e
      (let  [row (if (<= 3 (issues-count trial))
                   {:state "failed" :error "Too many issues, giving up to dispatch this trial "
                    :executor_id nil}
                   {:state "pending" :executor_id nil})]
        (trial-utils/update (conj trial row))
        false))))


(defn dispatch-trials []
  (when-let [next-trial  (get-next-trial-to-be-dispatched)]
    (loop [trial next-trial
           executor (choose-executor-to-dispatch-to trial)]
      (jdbc/update! (rdbms/get-ds) :trials
                    {:state "dispatching"
                     :executor_id (:id executor)}
                    ["id = ?" (:id trial)])
      (future (dispatch trial executor))
      (when-let [trial (get-next-trial-to-be-dispatched)]
        (recur trial (choose-executor-to-dispatch-to trial))))))


;#### dispatch service ########################################################
(daemon/define "dispatch-service"
  start-dispatch-service
  stop-dispatch-service
  0.2
  (logging/debug "dispatch-service")
  (dispatch-trials))

;### initialize ##############################################################
(defn initialize []
  (start-dispatch-service))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)

