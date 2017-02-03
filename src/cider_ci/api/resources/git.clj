; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.git
  (:require
    [cider-ci.api.pagination :as pagination]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [honeysql.sql :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(defn get-commit [request]
  (let [id (-> request :params :id)]
    (when-let [commit (-> (jdbc/query
                            (get-ds)
                            ["SELECT * FROM commits where id = ? " id])
                          first)]
      {:body commit})))

;##############################################################################

(def ^:private commits-base-query
  (-> (sql-select :commits.*)
      (sql-from :commits)
      (sql-modifiers :distinct)
      (sql-order-by [:commits.committer_date :desc]
                    [:commits.id])
      (sql-limit 10)))

(defn- filter-by-tree-id [request query]
  (if-let [tree-id (-> request :query-params :tree_id)]
    (-> query
        (sql-merge-where [:= :commits.tree_id tree-id]))
    query))


(defn filter-by-repository [request query]
  (if-let [repository-url (-> request :query-params :repository_url)]
    (-> query
        (sql-merge-join :branches_commits
                        [:= :branches_commits.commit_id :commits.id])
        (sql-merge-join [:branches :branches_via_branches_commits]
                        [:= :branches_via_branches_commits.id
                         :branches_commits.branch_id])
        (sql-merge-join :repositories
                        [:= :repositories.id
                         :branches_via_branches_commits.repository_id])
        (sql-merge-where [:= :repositories.git_url repository-url]))
    query))

(defn filter-by-branch-head [request query]
  (if-let [branch-name (-> request :query-params :branch_head)]
    (-> query
        (sql-merge-join :branches [:= :branches.current_commit_id
                                   :commits.id])
        (sql-merge-where [:=  :branches.name branch-name]))
    query))

(defn filter-by-branch-descendants [request query]
  (if-let [branch-name (-> request :query-params :branch_descendants)]
    (-> query
        (sql-merge-join :branches_commits
                        [:= :branches_commits.commit_id :commits.id])
        (sql-merge-join [:branches :branches_via_branches_commits]
                        [:= :branches_via_branches_commits.id :branches_commits.branch_id])
        (sql-merge-where [:= :branches_via_branches_commits.name branch-name]))
    query))

(defn get-commits [request]
  (let [query-params (:query-params request)
        commits (->> commits-base-query
                     debug/identity-with-logging
                     (#(pagination/add-offset-for-honeysql % query-params))
                     debug/identity-with-logging
                     (filter-by-tree-id request)
                     debug/identity-with-logging
                     (filter-by-repository request)
                     debug/identity-with-logging
                     (filter-by-branch-head request)
                     debug/identity-with-logging
                     sql-format
                     debug/identity-with-logging
                     (jdbc/query (get-ds)))]
    {:body {:commits commits}}))

(def routes
  (cpj/routes
    (cpj/GET "/commits/" _ get-commits)
    (cpj/GET "/commits/:id" _ get-commit)))




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)



