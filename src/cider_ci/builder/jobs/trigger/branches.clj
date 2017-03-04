; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger.branches
  (:require
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.duration :as duration]

    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [logbug.catcher :as catcher :refer [snatch]]
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))

(defn- branches [tree-id]
  (->> (-> (sql/select [:branches.name :name]
                       [:commits.committer_date :commited_at]
                       [:repositories.branch_trigger_max_commit_age :max_commit_age]
                       :branch_trigger_include_match
                       :branch_trigger_exclude_match)
           (sql/from :branches)
           (sql/merge-join :commits
                           [:= :branches.current_commit_id :commits.id])
           (sql/merge-join :repositories
                           [:= :branches.repository_id :repositories.id])
           (sql/where [:= :commits.tree_id tree-id])
           sql/format)
       (jdbc/query (rdbms/get-ds))))

(defn filter-includes [matcher-fn branches]
  (->> branches
       (filter (fn [branch]
                 (include-exclude/includes?
                   (matcher-fn branch)
                   (:name branch))))))

(defn filter-not-excludes [matcher-fn branches]
  (->> branches
       (filter (fn [branch]
                 (include-exclude/not-excludes?
                   (matcher-fn branch)
                   (:name branch))))))

(defn period-or-nil [branch duration-fn]
  (try
    (-> branch duration-fn duration/period)
    (catch Exception _ nil)))

(defn filter-max-commit-age [duration-fn branches]
  (->> branches
       (filter (fn [branch]
                 (when-let [commited-at (:commited_at branch)]
                   (if-let [period (period-or-nil branch duration-fn)]
                     (time/after? commited-at (time/minus (time/now) period))
                     true
                     ))))))

(defn- filter-by-trigger [trigger branches]
  (->> branches
       (filter-includes (fn [_] (:include_match trigger)))
       (filter-not-excludes (fn [_] (:exclude_match trigger)))
       (filter-max-commit-age (fn [branch] (:max_commit_age trigger)))))

(defn- filter-by-repository-params [branches]
  (->> branches
       (filter-includes (fn [branch] (:branch_trigger_include_match branch)))
       (filter-not-excludes (fn [branch] (:branch_trigger_exclude_match branch)))
       (filter-max-commit-age (fn [branch] (:max_commit_age branch)))))

(defn branch-trigger-fulfilled? [tree-id job trigger]
  (->> (-> tree-id branches)
       (filter-by-trigger trigger)
       filter-by-repository-params
       empty? not))

(defn branch-dependency-fulfilled? [tree-id job dependency]
  "Almost the same as branch-dependency-fulfilled? but does not honor
  the params defined in the repository."
  (->> (-> tree-id branches)
       (filter-by-trigger dependency)
       empty? not))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
