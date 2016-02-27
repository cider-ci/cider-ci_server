; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch.next-trial
  (:require
    [logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.sql :refer :all]
    ))

;##############################################################################

(def ^:private without-or-with-available-global-resource-condition
  [ "NOT EXISTS"
   (-> (sql-select 1)
       (sql-from [:trials :active_trials])
       (sql-merge-join [:tasks :active_tasks] [:= :active_tasks.id :active_trials.task_id])
       (sql-merge-where [:in :active_trials.state  ["executing","dispatching"]])
       (sql-merge-where (sql-raw (str "active_tasks.exclusive_global_resources "
                                      "&& tasks.exclusive_global_resources"))))])

(def ^:private trials-excutors-base-query
  (-> (sql-from :trials)
      (sql-merge-where [:= :trials.state "pending"])
      (sql-merge-join :tasks [:= :tasks.id :trials.task_id])
      (sql-merge-join [:executors_with_load :exs]
                      (sql-raw "(tasks.traits <@ exs.traits)"))
      (sql-select :trials.id [:exs.id :executor_id])
      (sql-merge-where [:< :exs.relative_load 1])
      (sql-merge-where [:= :exs.enabled true])
      (sql-merge-where (sql-raw (str "(exs.last_ping_at >  "
                                     "(now() - interval '1 Minutes'))")))
      (sql-merge-where without-or-with-available-global-resource-condition)))

(def ^:private trials-excutors-base-with-repo
  (-> trials-excutors-base-query
      (sql-merge-join :jobs [:= :tasks.job_id :jobs.id])
      (sql-merge-join :commits [:= :jobs.tree_id :commits.tree_id])
      (sql-merge-join [:branches_commits :bcts] [:= :commits.id :bcts.commit_id])
      (sql-merge-join :branches [:= :bcts.branch_id :branches.id])
      (sql-merge-join :repositories [:= :branches.repository_id :repositories.id])
      (sql-merge-where
        [:or
         (sql-raw "(exs.accepted_repositories = '{}')")
         (sql-raw " repositories.git_url = ANY(exs.accepted_repositories) ")])))

(def ^:private trials-excutors-query-ordered
  (-> trials-excutors-base-with-repo
      (sql-order-by [:jobs.priority :desc]
                    [:jobs.created_at :asc]
                    [:tasks.priority :desc]
                    [:tasks.created_at :asc]
                    [:trials.created_at :asc]
                    [:exs.relative_load :asc]
                    [:exs.last_ping_at :dsc])))

;##############################################################################

(defn next-trial-with-executor-for-push []
  (->> (-> trials-excutors-query-ordered
           (sql-merge-where [:<> :base_url ""])
           (sql-merge-where [:<> :base_url nil])
           (sql-limit 1)
           sql-format)
       (jdbc/query (rdbms/get-ds))
       first))

(defn next-trial-for-pull [tx executor]
  (->> (-> trials-excutors-query-ordered
           (sql-merge-where [:= :exs.id (:id executor)])
           (sql-merge-where [:or
                             [:= :base_url ""]
                             [:= :base_url nil]])
           (sql-limit 1)
           sql-format)
       (jdbc/query tx)
       first))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)
