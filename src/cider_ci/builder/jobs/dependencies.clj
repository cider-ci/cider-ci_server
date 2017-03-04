; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.dependencies
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.jobs.trigger.branches :as trigger.branches]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.util :as util]

    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [honeysql.core :as sql]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]

    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]

    ))


;##############################################################################

(defn set-un-runnable [job message]
  (assoc job
         :runnable false
         :reasons (conj (or (:reasons job) []) message)))

;##############################################################################

(defn- evaluate-name-clash [job]
  (if (seq (jdbc/query (get-ds)
                       ["SELECT 1 FROM jobs WHERE tree_id = ? AND name = ?"
                        (:tree_id job) (:name job)]))
    (set-un-runnable job "A job with the **same name** already exists for this tree-id.")
    job))

(defn- evaluate-key-clash [job]
  (if (seq (jdbc/query (get-ds)
                       ["SELECT 1 FROM jobs WHERE tree_id = ? AND key = ?"
                        (:tree_id job) (:key job)]))
    (set-un-runnable job "A job with the **same key** already exists for this tree-id.")
    job))

;##############################################################################

(defn- submodule-reducer [[query submodule_tree_id_join_ref] submodule-path]
  (let [commits_submodule_ref (str (gensym "commits_submodule_"))
        submodule_ref (str (gensym "submodules_"))
        commits_supermodule_ref (str (gensym "commits_supermodule_"))
        updated-query (-> query

               (sql/merge-join
                 [:commits commits_submodule_ref]
                 [:= (sql/raw (str commits_submodule_ref ".tree_id"))
                  (sql/raw (str submodule_tree_id_join_ref ".tree_id"))])

               (sql/merge-join
                 [:submodules submodule_ref]
                 [:= (sql/raw (str submodule_ref ".submodule_commit_id"))
                  (sql/raw (str commits_submodule_ref ".id"))])

               (sql/merge-join
                 [:commits commits_supermodule_ref]
                 [:= (sql/raw (str commits_supermodule_ref ".id"))
                  (sql/raw (str submodule_ref ".commit_id"))])

               (sql/merge-where
                 [:= (sql/raw (str submodule_ref ".path"))
                  submodule-path])

               )]
    [updated-query commits_supermodule_ref]))

(defn- subquery-for-job-depencency-in-submodules [base-query submodule-paths tree-id]
  (let [ [intermediate-query join_ref] (reduce submodule-reducer [base-query "jobs"] submodule-paths)]
    [" EXISTS "
     (-> intermediate-query
         (sql/merge-where [:= (sql/raw (str join_ref ".tree_id")) tree-id])) ]))

(defn- subquery-for-job-depencency [base-query tree-id]
  [" EXISTS "
   (-> base-query
       (sql/merge-where [:= :jobs.tree_id tree-id]))])

(defn- build-job-dependency-query [tree-id dependency]
  (let [base-query (-> (sql/select 1)
                       (sql/from :jobs)
                       (sql/merge-where [:= :jobs.key (:job_key dependency)])
                       (sql/merge-where [:in :jobs.state (:states dependency)]))
        subquery (if-let [submodule-paths (-> dependency :submodule seq)]
                   (subquery-for-job-depencency-in-submodules
                     base-query (reverse submodule-paths) tree-id)
                   (subquery-for-job-depencency base-query tree-id))]
    (-> base-query
        (sql/merge-where subquery)
        sql/format)))

(defn- evaluate-job-dependency [tree-id job dependency]
  (let [query (build-job-dependency-query tree-id dependency)]
    (if-not (seq (jdbc/query (get-ds) query))
      (set-un-runnable job (str "The dependency `" dependency "` is not fulfilled!"))
      job)))


;##############################################################################

(defn evaluate-branch-dependency [tree-id job dependency]
  (if-not (trigger.branches/branch-dependency-fulfilled? tree-id job dependency)
    (set-un-runnable job (str "The dependency `" dependency "` is not fulfilled!"))
    job))


;##############################################################################

(defn- evaluate-dependency [job dependency]
  (let [tree-id (:tree_id job)]
    (case (-> dependency :type to-cistr)
      "job" (evaluate-job-dependency tree-id job dependency)
      "branch" (evaluate-branch-dependency tree-id job dependency)
      (do
        (logging/warn "unhandled dependency" dependency)
        (set-un-runnable job (str "The type of the dependency `"
                                  dependency "` is not applicable!"))))))

(defn- evaluate-dependencies [job]
  (if-let [dependencies (->> job :depends_on (map second) seq)]
    (reduce evaluate-dependency job dependencies)
    job))

;##############################################################################

(defn evaluate [jobs]
  (->> jobs
       (map evaluate-name-clash)
       (map evaluate-key-clash)
       (map evaluate-dependencies)
       doall))

(defn fulfilled? [job]
  (-> job
      (assoc :runnable true)
      evaluate-name-clash
      evaluate-key-clash
      evaluate-dependencies
      :runnable))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
