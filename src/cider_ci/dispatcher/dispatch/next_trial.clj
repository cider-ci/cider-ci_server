; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch.next-trial
  (:require
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.format :as hsql-format]
    [honeysql.types :as hsql-types]
    [honeysql.helpers :as hsql-helpers]
    [robert.hooke :as hooke]
    ))


(defn choose-executor-to-dispatch-to [trial]
  (->> (-> (hsql-helpers/select :executors_with_load.*)
           (hsql-helpers/from :trials)
           (hsql-helpers/where [:= :trials.id (:id trial)])
           (hsql-helpers/merge-join :tasks [:= :tasks.id :trials.task_id])
           (hsql-helpers/merge-join :executors_with_load (hsql-types/raw "(tasks.traits <@ executors_with_load.traits)"))
           (hsql-helpers/merge-where [:<> :executors_with_load.base_url ""])
           (hsql-helpers/merge-where [:<> :executors_with_load.base_url nil])
           (hsql-helpers/merge-where (hsql-types/raw "(last_ping_at > (now() - interval '1 Minutes'))"))
           (hsql-helpers/merge-where [:= :enabled true])
           (hsql-helpers/merge-where [:< :relative_load 1])
           (hsql-helpers/merge-join :jobs [:= :tasks.job_id :jobs.id])
           (hsql-helpers/merge-join :commits [:= :jobs.tree_id :commits.tree_id])
           (hsql-helpers/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
           (hsql-helpers/merge-join :branches [:= :branches_commits.branch_id :branches.id])
           (hsql-helpers/merge-join :repositories [:= :branches.repository_id :repositories.id])
           (hsql-helpers/merge-where [:or
                           (hsql-types/raw "(executors_with_load.accepted_repositories = '{}')")
                           (hsql-types/raw " repositories.git_url = ANY(executors_with_load.accepted_repositories) ")])
           hsql-format/format)
       (jdbc/query (rdbms/get-ds))
       (map (fn [e] (repeat (- (:max_load e) (:current_load e)) e)))
       flatten rand-nth))

(def ^:private available-executor-exists-query
  (->
    (hsql-helpers/select 1)
    (hsql-helpers/from :executors_with_load)
    (hsql-helpers/merge-where [:< :relative_load 1])
    (hsql-helpers/merge-where [:= :enabled true])
    (hsql-helpers/merge-where [:<> :base_url ""])
    (hsql-helpers/merge-where [:<> :base_url nil])
    (hsql-helpers/merge-where (hsql-types/raw "(tasks.traits <@ executors_with_load.traits)"))
    (hsql-helpers/merge-where (hsql-types/raw "(last_ping_at > (now() - interval '1 Minutes'))"))))

(def ^:private join-trial-to-repo
  (-> (hsql-helpers/merge-join :tasks [:= :trials.task_id :tasks.id])
      (hsql-helpers/merge-join :jobs [:= :tasks.job_id :jobs.id])
      (hsql-helpers/merge-join :commits [:= :jobs.tree_id :commits.tree_id])
      (hsql-helpers/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
      (hsql-helpers/merge-join :branches [:= :branches_commits.branch_id :branches.id])
      (hsql-helpers/merge-join :repositories [:= :branches.repository_id :repositories.id])))


(def ^:private executor-with-accepted-repositories-part-query
  (-> join-trial-to-repo
      (hsql-helpers/select 1)
      (hsql-helpers/from :executors_with_load)
      (hsql-helpers/merge-where (hsql-types/raw " repositories.git_url = ANY(executors_with_load.accepted_repositories) "))))

(def ^:private available-executor-exists
  [:or [:exists (-> available-executor-exists-query
                    (hsql-helpers/merge-where (hsql-types/raw "(executors_with_load.accepted_repositories = '{}')")))]
   [:exists (merge
              available-executor-exists-query
              executor-with-accepted-repositories-part-query)]])

(def without-or-with-available-global-resource
  [ "NOT EXISTS" (-> (hsql-helpers/select 1)
                     (hsql-helpers/from [:trials :active_trials])
                     (hsql-helpers/merge-join [:tasks :active_tasks] [:= :active_tasks.id :active_trials.task_id])
                     (hsql-helpers/merge-where [:in :active_trials.state  ["executing","dispatching"]])
                     (hsql-helpers/merge-where (hsql-types/raw (str "active_tasks.exclusive_global_resources "
                                                  "&& tasks.exclusive_global_resources"))))])

(def next-trial-to-be-dispatched-base-query
  (-> (hsql-helpers/select :trials.*)
      (hsql-helpers/from :trials)
      (hsql-helpers/merge-where [:= :trials.state "pending"])
      (hsql-helpers/merge-where without-or-with-available-global-resource)
      (hsql-helpers/merge-join :tasks [:= :trials.task_id :tasks.id])
      (hsql-helpers/merge-join :jobs [:= :tasks.job_id :jobs.id])
      (hsql-helpers/merge-join :commits [:= :jobs.tree_id :commits.tree_id])
      (hsql-helpers/merge-join :branches_commits [:= :commits.id :branches_commits.commit_id])
      (hsql-helpers/merge-join :branches [:= :branches_commits.branch_id :branches.id])
      (hsql-helpers/merge-join :repositories [:= :branches.repository_id :repositories.id])
      (hsql-helpers/order-by [:jobs.priority :desc]
                             [:jobs.created_at :asc]
                             [:tasks.priority :desc]
                             [:tasks.created_at :asc]
                             [:trials.created_at :asc])
      (hsql-helpers/limit 1)))


(defn get-next-trial-to-be-dispatched []
  (-> next-trial-to-be-dispatched-base-query
      (hsql-helpers/merge-where available-executor-exists)
      hsql-format/format
      (#(jdbc/query (rdbms/get-ds) %))
      first))


