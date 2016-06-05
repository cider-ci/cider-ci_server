; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger
  (:require
    [cider-ci.builder.jobs :as jobs]
    [cider-ci.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.builder.project-configuration :as project-configuration]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.issues :refer [create-issue]]

    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.jdbc :refer [insert-or-update]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))


;### trigger jobs #######################################################

(defn- job-trigger-fulfilled? [tree-id job trigger]
  (logging/debug 'job-trigger-fulfilled? [tree-id job trigger])
  (let [query (-> (-> (sql/select true)
                      (sql/from :jobs)
                      (sql/merge-where [:= :tree_id tree-id])
                      (sql/merge-where [:= :key (:job_key trigger)])
                      (sql/merge-where [:in :state (or (:states trigger) ["passed"])])
                      (sql/limit 1)) sql/format) ]
    (logging/debug query)
    (->> query
         (jdbc/query (rdbms/get-ds))
         first
         boolean)))

(defn- branches-for-tree-id [tree-id]
  (->> (-> (sql/select :name :repository_id)
           (sql/from :branches)
           (sql/merge-join
             :commits
             [:= :branches.current_commit_id :commits.id])
           (sql/where [:= :commits.tree_id tree-id])
           sql/format)
       (jdbc/query (rdbms/get-ds))))

(defn- branch-trigger-fulfilled? [tree-id job trigger]
  (boolean
    (when-let [branches (-> tree-id branches-for-tree-id seq)]
      (let [repository (->> (-> (sql/select :branch_trigger_include_match
                                            :branch_trigger_exclude_match)
                                (sql/from :repositories)
                                (sql/merge-where [:in :id (map :repository_id branches)])
                                (sql/format))
                            (jdbc/query (rdbms/get-ds)) first)
            branch-names (map :name branches)]
        (->> branch-names
             (include-exclude/filter
               (:include_match trigger)
               (:exclude_match trigger))
             (include-exclude/filter
               (:branch_trigger_include_match repository)
               (:branch_trigger_exclude_match repository))
             first)))))

(defn trigger-fulfilled? [tree-id job trigger]
  (case (:type trigger)
    "job" (job-trigger-fulfilled? tree-id job trigger)
    "branch" (branch-trigger-fulfilled? tree-id job trigger)
    (do (logging/warn "unhandled run_when" trigger) false)))

(defn some-job-trigger-fulfilled? [tree-id job]
  (when-let [triggers (:run_when job)]
    (some (fn [[_ trigger]]
            (trigger-fulfilled? tree-id job trigger)) triggers)))


;##############################################################################

(defn- trigger-jobs [tree-id]
  (locking tree-id
    (catcher/snatch
      {:return-fn (fn [e] (create-issue "tree" tree-id e))}
      (I>> identity-with-logging
           (project-configuration/get-project-configuration tree-id)
           :jobs
           convert-to-array
           (filter #(-> % :run_when))
           (filter #(some-job-trigger-fulfilled? tree-id %))
           (map #(assoc % :tree_id tree-id))
           (filter jobs.dependencies/fulfilled?)
           (map jobs/create)
           doall)))
  tree-id)

;##############################################################################

(defn evaluate-tree-id-notifications []
  (I>> identity-with-logging
       "SELECT * FROM tree_id_notifications ORDER BY created_at ASC LIMIT 100"
       (jdbc/query (rdbms/get-ds))
       (map (fn [row]
              (future (trigger-jobs (:tree_id row))
                      row)))
       (map deref)
       (map :id)
       (map #(jdbc/delete! (rdbms/get-ds) :tree_id_notifications ["id = ?" %]))
       doall))

(defdaemon "evaluate-tree-id-notifications" 1 (evaluate-tree-id-notifications))


;### initialize ###############################################################

(defn initialize []
  (start-evaluate-tree-id-notifications))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
