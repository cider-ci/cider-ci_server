; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch.next-trial
  (:require
    [cider-ci.self]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    [honeysql.sql :refer :all]
    [cider-ci.utils.config :as config :refer [get-config]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))

;##############################################################################

(def ^:private without-or-with-available-global-resource-condition
  [ "NOT EXISTS"
   (-> (sql-select 1)
       (sql-from [:trials :active_trials])
       (sql-merge-join [:tasks :active_tasks]
                       [:= :active_tasks.id :active_trials.task_id])
       (sql-merge-where (sql-raw (str "active_trials.state IN "
                                      "('executing','dispatching')")))
       (sql-merge-where (sql-raw
                          (str "active_tasks.exclusive_global_resources "
                               "&& tasks.exclusive_global_resources"))))])

(def ^:private without-conflicting-dispatch-storm-delay
  [ "NOT EXISTS"
   (-> (sql-select 1)
       (sql-from [:trials :dispatch_storm_trials])
       (sql-merge-join [:tasks :dispatch_storm_tasks]
                       [:= :dispatch_storm_tasks.id :dispatch_storm_trials.task_id])
       (sql-merge-where (sql-raw (str "dispatch_storm_trials.state IN "
                                      "('executing','dispatching')")))
       (sql-merge-where [:= :dispatch_storm_trials.executor_id :exs.id])
       (sql-merge-where (sql-raw (str "( Coalesce("
                                      "   dispatch_storm_trials.dispatched_at, "
                                      "   dispatch_storm_trials.created_at) + "
                                      " ( interval '1 second'  "
                                      " * dispatch_storm_tasks.dispatch_storm_delay_seconds ))"
                                      " > now() "))))])

(defn ^:private with-defective-trial-delay []
  (if-let [interval (-> (get-config) :defective_trial_same_executor_redispatch_delay)]
    [ "NOT EXISTS"
     (-> (sql-select 1)
         (sql-from [:trials :dispatch_trials])
         (sql-merge-where [:= :dispatch_trials.id :trials.id])
         (sql-merge-join [:tasks :defective_tasks] [:= :defective_tasks.id :dispatch_trials.task_id])
         (sql-merge-join [:trials :trial_sibblings] [:= :trial_sibblings.task_id :defective_tasks.id])
         (sql-merge-where [:= :trial_sibblings.state  "defective"])
         (sql-merge-where (sql-raw (str "( trial_sibblings.updated_at > now() - (interval '"
                                        interval "'))"))))]
    (sql-raw "true")))

(defn ^:private trials-excutors-base-query []
  (-> (sql-from :trials)
      (sql-merge-where (sql-raw "trials.state = 'pending'"))
      (sql-merge-join :tasks [:= :tasks.id :trials.task_id])
      (sql-merge-join [:executors_with_load :exs]
                      (sql-raw "(tasks.traits <@ exs.traits)"))
      (sql-select :trials.id :trials.task_id [:exs.id :executor_id])
      (sql-merge-where [:< :exs.relative_load 1])
      (sql-merge-where (sql-raw (str "tasks.load < "
                                     "exs.max_load * exs.temporary_overload_factor "
                                     " - exs.current_load ")))
      (sql-merge-where [:= :exs.enabled true])
      (sql-merge-where (sql-raw (str "(exs.last_ping_at >  "
                                     "(now() - interval '1 Minutes'))")))
      (sql-merge-where without-conflicting-dispatch-storm-delay)
      (sql-merge-where without-or-with-available-global-resource-condition)
      (sql-merge-where (with-defective-trial-delay))
      ))

(defn ^:private trials-excutors-base-with-repo []
  (-> (trials-excutors-base-query)
      (sql-merge-join :jobs [:= :tasks.job_id :jobs.id])
      (sql-merge-join :commits [:= :jobs.tree_id :commits.tree_id])
      (sql-merge-join [:branches_commits :bcts]
                      [:= :commits.id :bcts.commit_id])
      (sql-merge-join :branches [:= :bcts.branch_id :branches.id])
      (sql-merge-join :repositories
                      [:= :branches.repository_id :repositories.id])
      (sql-merge-where
        [:or (sql-raw "(exs.accepted_repositories = '{}')")
             (sql-raw (str " repositories.git_url ="
                           " ANY(exs.accepted_repositories) "))])))

(defn ^:private trials-excutors-query-ordered []
  (-> (trials-excutors-base-with-repo)
      (sql-order-by [:jobs.priority :desc]
                    [:jobs.created_at :asc]
                    [:tasks.priority :desc]
                    [:tasks.created_at :asc]
                    [:trials.created_at :asc]
                    [:tasks.name :asc])))

;##############################################################################

(defn next-trial-for-pull [tx executor]
  (->> (-> (trials-excutors-query-ordered)
           (sql-merge-where [:= :exs.id (:id executor)])
           (sql-merge-where [:= cider-ci.self/VERSION (:version executor)])
           (sql-limit 1) sql-format)
       identity-with-logging
       (jdbc/query tx)
       first))

;#### debug ###################################################################
;(debug/wrap-with-log-debug #'next-trial-for-pull)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)
