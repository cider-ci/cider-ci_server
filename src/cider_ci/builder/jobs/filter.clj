; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.filter
  (:require
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.jobs.tags :as tags]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.util :as util]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [cider-ci.utils.map :refer [deep-merge convert-to-array]]
    [clj-logging-config.log4j :as logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.sql :refer :all]
    ))


;### self filter ##############################################################

(defn- add-self-name-filter-to-query [query name tree-id]
  (-> query
      (sql-merge-where
        ["NOT EXISTS"
         (-> (sql-select 1)
             (sql-from :jobs)
             (sql-where [:= :jobs.name name])
             (sql-merge-where [:= :jobs.tree_id tree-id]))])))


;### job dependencies #########################################################

(defn- submodule-reducer [[query submodule_tree_id_join_ref] submodule-path]
  (let [commits_submodule_ref (str (gensym "commits_submodule_"))
        submodule_ref (str (gensym "submodules_"))
        commits_supermodule_ref (str (gensym "commits_supermodule_"))
        updated-query (-> query

               (sql-merge-join
                 [:commits commits_submodule_ref]
                 [:= (sql-raw (str commits_submodule_ref ".tree_id"))
                  (sql-raw (str submodule_tree_id_join_ref ".tree_id"))])

               (sql-merge-join
                 [:submodules submodule_ref]
                 [:= (sql-raw (str submodule_ref ".submodule_commit_id"))
                  (sql-raw (str commits_submodule_ref ".id"))])

               (sql-merge-join
                 [:commits commits_supermodule_ref]
                 [:= (sql-raw (str commits_supermodule_ref ".id"))
                  (sql-raw (str submodule_ref ".commit_id"))])

               (sql-merge-where
                 [:= (sql-raw (str submodule_ref ".path"))
                  submodule-path])

               )]
    [updated-query commits_supermodule_ref]))

(defn- subquery-for-job-depencency-in-submodules [base-query submodule-paths tree-id]
  (let [ [intermediate-query join_ref] (reduce submodule-reducer [base-query "jobs"] submodule-paths)]
    [" EXISTS "
     (-> intermediate-query
         (sql-merge-where [:= (sql-raw (str join_ref ".tree_id")) tree-id])) ]))

(defn- subquery-for-job-depencency [base-query tree-id]
  [" EXISTS "
   (-> base-query
       (sql-merge-where [:= :jobs.tree_id tree-id]))])

(defn- apply-job-depenency-to-query [query dependency tree-id]
  (let [base-query (-> (sql-select 1)
                       (sql-from :jobs)
                       (sql-merge-where [:= :jobs.key (:job dependency)])
                       (sql-merge-where [:in :jobs.state (:states dependency)]))
        subquery (if-let [submodule-paths (-> dependency :submodule seq)]
                   (subquery-for-job-depencency-in-submodules
                     base-query (reverse submodule-paths) tree-id)
                   (subquery-for-job-depencency base-query tree-id))]
    (-> query (sql-merge-where subquery))))


;##############################################################################

(defn- apply-dependency [query dependency tree-id]
  (case (:type dependency)
    "job" (apply-job-depenency-to-query query dependency tree-id)
    (throw (IllegalStateException. (str "Unknown dependency " dependency)))))

(defn- build-dependency-reducer [tree-id]
  (fn [query dependency]
    (logging/debug 'dependency-reducer-invoke [query dependency tree-id])
    (let [res-query (apply-dependency query dependency tree-id)]
      (logging/debug 'dependency-reducer-result res-query)
      res-query)))



;##############################################################################

(defn dependencies-fulfilled? [tree-id properties]
  (let [initial-query (add-self-name-filter-to-query (sql-select :true) (:name properties) tree-id)
        reducer (build-dependency-reducer tree-id)
        final-query (reduce reducer initial-query (-> properties :depends-on convert-to-array))
        formated-query (-> final-query sql-format) ]
    (logging/debug 'formated-query formated-query)
    (->> formated-query
         (jdbc/query (rdbms/get-ds))
         first
         :bool)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
