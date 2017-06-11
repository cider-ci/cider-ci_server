; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.jobs.dependencies.branch
  (:require
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.duration :as duration]
    [cider-ci.utils.map :refer [convert-to-array]]


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

(defn- filter-by-trigger [trigger branches]
  (->> branches
       (filter-includes (fn [_] (:include_match trigger)))
       (filter-not-excludes (fn [_] (:exclude_match trigger)))))


; #############################################################################

(defn fulfilled? [tree-id job dependency]
  (->> (-> tree-id branches)
       (filter-by-trigger dependency)
       empty? not))



;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
