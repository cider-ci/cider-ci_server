; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.tree-commits.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require

    [cider-ci.auth.authorize :as authorize]

    [cheshire.core]
    [compojure.core :as cpj]
    [honeysql.core :as sql]
    [honeysql.helpers :as sql2]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

;;; helper ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sql-format sql/format)

(defn heads-only [query request]
  (if (-> request :query-params :heads_only)
    (sql/merge-join query :branches [:= :commits.id :branches.current_commit_id ])
    query))

(defn orphans [query request]
  (if (-> request :query-params :orphans)
    query
    (sql/merge-where
      query
      [:exists (-> (sql/select true)
                   (sql/from :branches_commits)
                   (sql/merge-where [:= :branches_commits.commit_id :commits.id]))])))


;;;       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def trees-base-query
  (-> (sql/select [:commits.tree_id :tree_id]
                  (sql/raw "max(commits.committer_date) AS date"))
      (sql/from [:commits :commits])
      (sql/offset 0)
      (sql/limit 5)
      (sql2/group :commits.tree_id)
      (sql/order-by [:date :desc]
                    [:tree_id :desc])))

(defn trees [request]
  (->> (-> trees-base-query
           (heads-only request)
           (orphans request)
           sql-format)
       (jdbc/query (rdbms/get-ds))))

(defn add-branches-to-remote [commit remote]
  (assoc
    remote
    :branches
    (->> (-> (sql/select [:branches.name :name]
                         (sql/raw (str "( current_commits.depth - " (:depth commit)
                                       " ) AS distance_to_head ")))
             (sql/from :branches)
             (sql/merge-where [:= :branches.repository_id (:id remote)])
             (sql/merge-join :branches_commits [:= :branches_commits.branch_id :branches.id])
             (sql/merge-where [:= :branches_commits.commit_id (:id commit)])
             (sql/merge-join [:commits :current_commits]
                             [:= :current_commits.id :branches.current_commit_id])
             sql-format)
         (jdbc/query (rdbms/get-ds)))))

(defn add-projects [commit]
  (assoc commit :projects
         (->> (-> (sql/select :repositories.name
                              :repositories.id
                              [:repositories.git_url :git_url]
                              [:repositories.remote_api_type :type])
                  (sql/modifiers :distinct)
                  (sql/from :commits)
                  (sql/merge-where [:= :commits.id (:id commit)])
                  (sql/merge-join :branches_commits
                                  [:= :branches_commits.commit_id :commits.id])
                  (sql/merge-join :branches
                                  [:= :branches_commits.branch_id :branches.id])
                  (sql/merge-join :repositories
                                  [:= :branches.repository_id :repositories.id])
                  (sql/order-by [:repositories.name :asc] [:repositories.id :asc])
                  sql-format)
              (jdbc/query (rdbms/get-ds))
              (map #(add-branches-to-remote commit %))
              )))


;;; trees ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn commits-for-tree [tree request]
  (->> (-> (sql/select :*)
           (sql/modifiers :distinct)
           (sql/from :commits)
           (sql/merge-where [:= :tree_id (:tree_id tree)])
           (heads-only request)
           (orphans request)
           (sql/order-by [:committer_date :desc]
                         [:tree_id :desc])
           sql-format)
       (jdbc/query (rdbms/get-ds))
       (map add-projects)
       ))

(defn add-commits [request trees]
  (map (fn [tree]
         (assoc tree :commits (commits-for-tree tree request)))
       trees))

(defn tree-commits [request]
  {:body (->> request
              trees
              (add-commits request))})


;(commits {:query-params {:heads_only true :orphans false}})

(defn canonicalize-query-params [request]
  (if-let [query-params (:query-params request)]
    (assoc request :query-params
           (->> query-params
                (map (fn [[k v]] [k (cheshire.core/parse-string v)]))
                (into {})
                clojure.walk/keywordize-keys))
    request))

(defn wrap-canonicalize-query-params [handler]
  (fn [request]
    (-> request canonicalize-query-params handler)))

(def routes
  (wrap-canonicalize-query-params
    (cpj/routes
      (cpj/GET  "/tree-commits/" _
               #'tree-commits
               ;#'(authorize/wrap-require! #'commits {:user true})
               ))))

;(cheshire.core/parse-string "\"blha\"")
;(cheshire.core/parse-string "1")
;(json/read-str "blah")

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)

