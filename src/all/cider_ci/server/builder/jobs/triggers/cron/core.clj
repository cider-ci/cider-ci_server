; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.jobs.triggers.cron.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.jobs :as jobs]
    [cider-ci.server.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.server.builder.jobs.triggers.shared :as shared]
    [cider-ci.shared.cron]
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(def query
  (-> (sql/select :tree_id
                  [:branches.name :branch_name]
                  [:branches.updated_at :branch_updated_at]
                  [:repositories.git_url :repository_git_url]
                  [:repositories.name :repository_name])
      (sql/modifiers :distinct)
      (sql/from :commits)
      (sql/merge-join :branches [:= :branches.current_commit_id :commits.id])
      (sql/merge-join :repositories [:= :repositories.id :branches.repository_id])
      (sql/merge-where [:= :repositories.cron_trigger_enabled true])
      sql/format))

;##############################################################################

(defn cron-branch-include-exclude-satisfied? [event cron-run-when]
  (and (include-exclude/not-excludes?
         (:branch_exclude_match cron-run-when)
         (:branch_name event))
       (include-exclude/includes?
         (:branch_include_match cron-run-when)
         (:branch_name event))))


;##############################################################################


(defn- cron-run-when-fulfilled? [cron-run-when]
  (cider-ci.shared.cron/fire? (:value cron-run-when) 3))

(defn- multiply-with-satisfied-run-whens [event job-config]
  (->> job-config :run_when convert-to-array
       (filter (fn [rw] (= (-> rw :type keyword) :cron)))
       (filter cron-run-when-fulfilled?)
       (filter #(cron-branch-include-exclude-satisfied? event %))
       (map #(assoc job-config :run_when_trigger %))
       seq))

(defn- combine-with-fullfiled-run-whens [event jobs]
  (->> jobs (map #(multiply-with-satisfied-run-whens event %))
       flatten (filter identity) seq))

(defn triggered-jobs [event]
  (->> (shared/filtered-run-when-event-type-jobs (:tree_id event) "cron")
       (combine-with-fullfiled-run-whens event) doall seq))

;##############################################################################

(defn job-does-not-exists-yet? [job-config tx]
  (->> [(str "SELECT true AS exists_already FROM jobs "
             "WHERE tree_id = ? AND key = ? ")
        (:tree_id job-config) (:key job-config)]
       (jdbc/query tx) first :exists_already not))

(defn delete-query [job-spec]
  (-> (sql/delete-from :jobs)
      (sql/merge-where
        (sql/raw " (updated_at < (now() - interval '3 Minutes')) "))
      (sql/merge-where
        (sql/raw
          " state IN ('aborted', 'defective', 'failed', 'passed', 'skipped')"))
      (sql/merge-where [:= :tree_id (:tree_id job-spec)])
      (sql/merge-where [:= :key (:key job-spec)])
      sql/format))

(defn create-job [job-spec]
  (locking (-> job-spec :tree_id str)
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (whon (-> job-spec :run_when_trigger :rerun)
        (jdbc/execute! tx (delete-query job-spec)))
      (when (and (jobs.dependencies/fulfilled? job-spec)
                 (job-does-not-exists-yet? job-spec tx))
        (jobs/create job-spec tx)))))


;##############################################################################

(defn- create-jobs-for-cron-event [event]
  (->> event triggered-jobs (map create-job) doall))

;##############################################################################



(defn cron-branch-trigger []
  ;(debug/I>> debug/identity-with-logging
  (->>
    query
    (jdbc/query (rdbms/get-ds))
    ;(map #(catcher/snatch {} (trigger-jobs %)))
    (map create-jobs-for-cron-event)
    doall))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
