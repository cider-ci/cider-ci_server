; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.triggers.tree-ids.branch-update
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])

  (:require
    [cider-ci.builder.jobs.triggers.shared :as shared]

    [cider-ci.utils.duration :as duration]
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-time.core :as time]
    [honeysql.core :as sql]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
  ))


(defn- base-query [ids]
  (-> (sql/select
        (sql/raw " 'branch' AS type ")
        [:branches.name :branch_name]
        [:branches.updated_at :branch_updated_at]
        [:commits.committer_date :commited_at]
        [:repositories.branch_trigger_exclude_match :repository_branch_trigger_exclude_match]
        [:repositories.branch_trigger_include_match :repository_branch_trigger_include_match]
        [:repositories.branch_trigger_max_commit_age :repository_branch_trigger_max_commit_age]
        [:repositories.git_url :repository_git_url]
        [:repositories.name :repository_name]
        [:tree_id_notifications.id :id]
        [:tree_id_notifications.tree_id :tree_id])
      (sql/from :tree_id_notifications)
      (sql/merge-join :branches [:= :branches.id :tree_id_notifications.branch_id])
      (sql/merge-join :commits [:= :commits.id :branches.current_commit_id])
      (sql/merge-join :repositories [:= :repositories.id :branches.repository_id])
      (sql/merge-where [:in :tree_id_notifications.id ids])
      sql/format))

;(base-query "x")

;##############################################################################

(defn- period-or-nil [duration]
  (try
    (duration/period duration)
    (catch Exception _ nil)))

(defn- repository-max-commit-age-satisfied? [event]
  (boolean
    (if-let [max-age-priod (-> event
                               :repository_branch_trigger_max_commit_age
                               period-or-nil)]
      (time/after? (:commited_at event)
                   (time/minus (time/now) max-age-priod))
      true)))

;##############################################################################

(defn- repository-branch-include-satisfied? [event]
  (include-exclude/includes?
    (:repository_branch_trigger_include_match event)
    (:branch_name event)))

(defn- repository-branch-not-exclude-satisfied? [event]
  (include-exclude/not-excludes?
    (:repository_branch_trigger_exclude_match event)
    (:branch_name event)))

(defn- repository-branch-include-exclude-satisfied? [event]
  (and (repository-branch-include-satisfied? event)
       (repository-branch-not-exclude-satisfied? event)))


;##############################################################################

(defn- repository-restrictions-for-branch-event-satisfied? [event]
  (and (repository-max-commit-age-satisfied? event)
       (repository-branch-include-exclude-satisfied? event)))

(defn some-branch-run-when-fulfilled? [branch-name job]
  (->> job :run_when convert-to-array
       (filter (fn [rw] (= (-> rw :type keyword) :branch)))
       (some (fn [brw]
               (and (include-exclude/not-excludes? (:exclude_match brw) branch-name)
                    (include-exclude/includes? (:include_match brw) branch-name))))))

(defn triggered-jobs [event]
  (when (repository-restrictions-for-branch-event-satisfied? event)
    (->> (shared/filtered-run-when-event-type-jobs
           (:tree_id event) (:type event))
         (filter #(some-branch-run-when-fulfilled? (:branch_name event) %))
         seq)))

;##############################################################################


(defn- create-jobs-for-branch-update [event]
  (->> event triggered-jobs shared/create-jobs))

;##############################################################################

(defn build-and-trigger [tx tree-id-notification-ids]
  ;(debug/I>> debug/identity-with-logging
  (->> (base-query tree-id-notification-ids)
       (jdbc/query tx)
       (map #(update-in % [:type] keyword))
       (map create-jobs-for-branch-update)
       doall))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

