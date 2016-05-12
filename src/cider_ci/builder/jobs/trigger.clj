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
    [cider-ci.utils.http :as http]
    [cider-ci.utils.include-exclude :as include-exclude]
    [cider-ci.utils.jdbc :refer [insert-or-update]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug]
    ))


;### trigger jobs #######################################################

(defn- job-trigger-fulfilled? [tree-id job trigger]
  (logging/debug 'job-trigger-fulfilled? [tree-id job trigger])
  (let [query (-> (-> (sql/select true)
                      (sql/from :jobs)
                      (sql/merge-where [:= :tree_id tree-id])
                      (sql/merge-where [:= :key (:job trigger)])
                      (sql/merge-where [:in :state (:states trigger)])
                      (sql/limit 1)) sql/format) ]
    (logging/debug query)
    (->> query
         (jdbc/query (rdbms/get-ds))
         first
         boolean)))

(defn- branch-trigger-fulfilled? [tree-id job trigger]
  (let [branches-query (-> (sql/select :name :repository_id)
                           (sql/from :branches)
                           (sql/merge-join
                             :commits
                             [:= :branches.current_commit_id :commits.id])
                           (sql/where [:= :commits.tree_id tree-id])
                           sql/format)
        branches (jdbc/query (rdbms/get-ds) branches-query)
        repository (->> (-> (sql/select :branch_trigger_include_match
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
         first
         boolean)))

(defn trigger-fulfilled? [tree-id job trigger]
  (case (:type trigger)
    "job" (job-trigger-fulfilled? tree-id job trigger)
    "branch" (branch-trigger-fulfilled? tree-id job trigger)
    (do (logging/warn "unhandled run_on" trigger) false)))

(defn some-job-trigger-fulfilled? [tree-id job]
  (let [triggers (:run_on job)]
    (if (= true triggers)
      true
      (some (fn [trigger]
              (trigger-fulfilled? tree-id job trigger)) triggers))))


;##############################################################################

(declare trigger-supermodules-jobs)

(defn- trigger-jobs [tree-id]
  (catcher/snatch
    {:return-fn (fn [e] (create-issue "tree" tree-id e))}
    (->> (project-configuration/get-project-configuration tree-id)
         :jobs
         convert-to-array
         (filter #(-> % :run_on))
         (filter #(some-job-trigger-fulfilled? tree-id %))
         (map #(assoc % :tree_id tree-id))
         (filter jobs.dependencies/fulfilled?)
         (map jobs/create)
         doall))
  (catcher/snatch {}
                  (trigger-supermodules-jobs tree-id)) nil)

(defn- trigger-supermodules-jobs [tree-id]
  (->> (jdbc/query (rdbms/get-ds)
              ["SELECT DISTINCT supermodules_commits.tree_id FROM commits AS supermodules_commits
                JOIN submodules ON submodules.commit_id = supermodules_commits.id
                JOIN commits AS submodule_commits ON submodule_commits.id = submodules.submodule_commit_id
                WHERE submodule_commits.tree_id = ?" tree-id])
       (map :tree_id)
       (map trigger-jobs)
       doall) nil)


;### listen to branch updates #################################################

(defn- evaluate-branch-updated-message [msg]
  (catcher/with-logging {}
    (logging/debug 'evaluate-branch-updated-message {:msg msg})
    (-> (jdbc/query
          (rdbms/get-ds)
          ["SELECT tree_id FROM commits WHERE id = ? " (:current_commit_id msg)])
        first
        :tree_id
        trigger-jobs)))

(defn listen-to-branch-updates-and-fire-trigger-jobs []
  (messaging/listen "branch.updated" evaluate-branch-updated-message))

(defn listen-to-branch-creations-and-fire-trigger-jobs []
  (messaging/listen "branch.created" evaluate-branch-updated-message))


;### listen to job updates ##############################################

(defn evaluate-job-update [msg]
  (-> (jdbc/query
        (rdbms/get-ds)
        ["SELECT tree_id FROM jobs WHERE id = ? " (:id msg)])
      first
      :tree_id
      trigger-jobs))

(defn listen-to-job-updates-and-fire-trigger-jobs []
  (messaging/listen "job.updated" evaluate-job-update))


;### initialize ###############################################################

(defn initialize []
  (listen-to-branch-updates-and-fire-trigger-jobs)
  (listen-to-branch-creations-and-fire-trigger-jobs)
  (listen-to-job-updates-and-fire-trigger-jobs))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
