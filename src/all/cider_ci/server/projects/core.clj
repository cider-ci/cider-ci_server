; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.core
  (:refer-clojure :exclude [str keyword resolve])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))



(defonce projects* (atom {}))



(defn- resolve-query [git-id]
  (-> (sql/select [:projects.id :project_id] :commits.committer_date)
      (sql/from :projects)
      (sql/merge-join :branches [:= :branches.project_id :projects.id])
      (sql/merge-join :branches_commits [:= :branches_commits.branch_id :branches.id])
      (sql/merge-join :commits [:= :commits.id :branches_commits.commit_id])
      (sql/merge-where [:or 
                        [:= :commits.id git-id]
                        [:= :commits.tree_id git-id]])

      (sql/limit 1)
      (sql/order-by [:commits.committer_date :desc])
      sql/format))

(defn resolve-project [git-id & [{tx :tx
                          :or {tx @rdbms/ds}}]]
  "Returns a project given a sha1 commit-id or tree-id."

  (get @projects* (->> git-id resolve-query (jdbc/query tx) first :project_id)))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- resolve-commit-id-query [git-id]
  (-> (sql/select [:commits.id :commit_id] :commits.committer_date)
      (sql/from :commits)
      (sql/merge-where [:or 
                        [:= :commits.id git-id]
                        [:= :commits.tree_id git-id]])

      (sql/limit 1)
      (sql/order-by [:commits.committer_date :desc])
      sql/format))

(defn resolve-commit-id [git-id & [{tx :tx
                                    :or {tx @rdbms/ds}}]]
  "Returns a commit-id for a given tree-id or commit-id."
  (->> git-id resolve-commit-id-query (jdbc/query tx) first :commit_id))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(def ^:dynamic *caching-enabled* false)
;(debug/debug-ns *ns*)
