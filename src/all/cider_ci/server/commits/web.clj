; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.commits.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [presence keyword str]])
  (:require

    [cider-ci.auth.authorize :as authorize]

    [cheshire.core]
    [compojure.core :as cpj]
    [cider-ci.utils.honeysql :as sql]
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

(defn heads-only [query request]
  (if (-> request :query-params :heads-only)
    (-> query
        (sql/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (sql/merge-join :branches [:= :branches.id :branches_commits.branch_id])
        (sql/merge-where [:= :branches.current_commit_id :commits.id]))
    query))

(defn orphans [query request]
  (if (-> request :query-params :orphans)
    query
    (sql/merge-where
      query
      [:exists (-> (sql/select true)
                   (sql/from :branches_commits)
                   (sql/merge-where [:= :branches_commits.commit_id :commits.id]))])))

(defn filter-by-project-name [query {{project-name :project-name
                                         as-regex :project-name-as-regex}
                                        :query-params}]
  (if (presence project-name)
    (-> query
        (sql/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (sql/merge-join :branches [:= :branches.id :branches_commits.branch_id])
        (sql/merge-join :repositories [:= :repositories.id :branches.repository_id])
        ((fn [query]
           (if as-regex
             (sql/merge-where query ["~*" :repositories.name project-name])
             (sql/merge-where query [:= :repositories.name project-name])))))
    query))

(defn filter-by-branch-name [query {{branch-name :branch-name
                                     as-regex :branch-name-as-regex}
                                    :query-params}]
  (if (presence branch-name)
    (-> query
        (sql/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (sql/merge-join :branches [:= :branches.id :branches_commits.branch_id])
        ((fn [query]
           (if as-regex
             (sql/merge-where query ["~*" :branches.name branch-name])
             (sql/merge-where query [:= :branches.name branch-name])))))
    query))


;;;       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def trees-base-query
  (-> (sql/select [:commits.tree_id :tree_id]
                  (sql/raw "max(commits.committer_date) AS date"))
      (sql/from [:commits :commits])
      (sql/offset 0)
      (sql/limit 5)
      (sql/group :commits.tree_id)
      (sql/order-by [:date :desc]
                    [:tree_id :desc])))

(defn set-limit [query request]
  (let [limit (-> (-> request :query-params :per-page)
                  (or 12)
                  (min 100))]
    (-> query (sql/limit limit))))

(defn trees [request]
  (->> (-> trees-base-query
           (set-limit request)
           (filter-by-project-name request)
           (filter-by-branch-name request)
           (heads-only request)
           (orphans request)
           sql/format)
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
             sql/format)
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
                  sql/format)
              (jdbc/query (rdbms/get-ds))
              (map #(add-branches-to-remote commit %))
              )))


;;; trees ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn commits-for-tree [tree request]
  (->> (-> (sql/select :commits.*)
           (sql/modifiers :distinct)
           (sql/from :commits)
           (sql/merge-where [:= :tree_id (:tree_id tree)])
           (heads-only request)
           (orphans request)
           (sql/order-by [:committer_date :desc]
                         [:tree_id :desc])
           sql/format)
       (jdbc/query (rdbms/get-ds))
       (map add-projects)
       ))

(defn add-commits [request trees]
  (map (fn [tree]
         (assoc tree :commits (commits-for-tree tree request)))
       trees))

(defn commits [request]
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

;;; project and branch-names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn project-and-branchnames [request]
  {:body
   (->> (-> (sql/select [:repositories.name :project]
                        (sql/raw "array_agg(branches.name) branches "))
            (sql/from :repositories)
            (sql/merge-join :branches [:= :repositories.id :branches.repository_id])
            (sql/group :repositories.name)
            sql/format)
        (jdbc/query (rdbms/get-ds))
        (map (fn [r] [(:project r)(:branches r)])))})


;;; jobs-summaries ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn jobs-summaries [request]
  (let [tree-ids (-> request :query-params :tree-ids)]
    (if (empty? tree-ids)
      {:body {}}
      (let [query  (-> (sql/select :jobs.tree_id :jobs.state :%count.state)
                       (sql/from :jobs)
                       (sql/merge-where [:in :tree_id tree-ids])
                       (sql/group :tree_id :state)
                       sql/format)
            res (->> query
                     (jdbc/query (rdbms/get-ds))
                     (reduce (fn [agg x]
                               (assoc-in agg [(:tree_id x) (:state x)] (:count x))) {}))]
        ;(logging/warn tree-ids)
        ;(logging/warn query)
        ;(logging/warn (jdbc/query (rdbms/get-ds) query))
        {:body res}))))

;(debug/re-apply-last-argument #'jobs-summaries)


;;; routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  (wrap-canonicalize-query-params
    (cpj/routes
      (cpj/GET  "/commits/" _
               #'commits
               ;#'(authorize/wrap-require! #'commits {:user true})
               )
      (cpj/GET  "/commits/project-and-branchnames/" _
               #'project-and-branchnames
               ;#'(authorize/wrap-require! #'commits {:user true})
               )
      (cpj/GET  "/commits/jobs-summaries/" _
               #'jobs-summaries
               ;#'(authorize/wrap-require! #'commits {:user true})
               ))))

;(cheshire.core/parse-string "\"blha\"")
;(cheshire.core/parse-string "1")
;(json/read-str "blah")

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)
