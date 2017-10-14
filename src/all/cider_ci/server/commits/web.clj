; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.commits.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [presence keyword str]])
  (:require

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.ring :refer [wrap-canonicalize-query-params]]

    [cheshire.core]
    [compojure.core :as cpj]
    [cider-ci.utils.honeysql :as sql]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


;;; helper ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn heads-only [query request]
  (let [query-params (:query-params request)]
    (if (if (contains? query-params :heads-only)
          (:heads-only query-params)
          true)
      (-> query
          (sql/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
          (sql/merge-join :branches [:= :branches.id :branches_commits.branch_id])
          (sql/merge-where [:= :branches.current_commit_id :commits.id]))
      query)))

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

(defn filter-by-email
  [query {{email :email
           as-regex :email-as-regex}
          :query-params}]
  (if (presence email)
    (let [op (if as-regex "~*" :=)]
      (sql/merge-where
        query
        [:or
         [op :commits.author_email email]
         [op :commits.committer_email email]]))
    query))

(defn email-addresses-subquery [user-id]
  (-> (sql/select :%lower.email_address)
      (sql/from :email_addresses)
      (sql/merge-where [:= :email_addresses.user_id user-id])))

;(sql/format (email-addresses-subquery "7fa010bc-2680-490e-8470-b85ccc46d502"))

(defn filter-by-my-commits
  [query {{my-commits :my-commits} :query-params
          {type :type id :id}:authenticated-entity}]
  (if (and (= type :user)
           (-> my-commits presence boolean))
    (sql/merge-where
      query [:or
             [:in :%lower.commits.author_email (email-addresses-subquery id)]
             [:in :%lower.commits.committer_email (email-addresses-subquery id)]])
    query))

;(sql/format (filter-by-my-commits
;  (sql/select :*)
;  {:authenticated-entity {:type :user :id "7fa010bc-2680-490e-8470-b85ccc46d502"}
;   :query-params {:my-commits true}}))

(defn filter-by-git-ref
  [query {{git-ref :git-ref
           as-regex :git-ref-as-regex}
          :query-params}]
  (if (presence git-ref)
    (let [op (if as-regex "~*" :=)]
      (sql/merge-where
        query
        [:or
         [op :commits.id git-ref]
         [op :commits.tree_id git-ref]]))
    query))

;;;       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def trees-base-query
  (-> (sql/select [:commits.tree_id :tree_id]
                  (sql/raw "max(commits.committer_date) AS date"))
      (sql/from [:commits :commits])
      (sql/group :commits.tree_id)
      (sql/order-by [:date :desc]
                    [:tree_id :desc])))

(defn set-per-page-and-offset
  [query {{per-page :per-page page :page} :query-params}]
  (when (or (-> per-page presence not)
            (-> per-page integer? not)
            (> per-page 100)
            (< per-page 1))
    (throw (ex-info "The query parameter per-page must be present and set to an integer between 1 and 100."
                    {:status 422})))
  (when (or (-> page presence not)
            (-> page integer? not)
            (< page 0))
    (throw (ex-info "The query parameter page must be present and set to a positive integer."
                    {:status 422})))
  (-> query
      (sql/limit per-page)
      (sql/offset (* per-page (- page 1)))))


(defn trees [request]
  (->> (-> trees-base-query
           (set-per-page-and-offset request)
           (filter-by-project-name request)
           (filter-by-branch-name request)
           (filter-by-email request)
           (filter-by-my-commits request)
           (filter-by-git-ref request)
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
        {:body res}))))


;;; routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  (-> (cpj/routes
        (cpj/GET  "/commits/" _ #'commits)
        (cpj/GET  "/commits/project-and-branchnames/" _ #'project-and-branchnames)
        (cpj/GET  "/commits/jobs-summaries/" _ #'jobs-summaries))
      wrap-canonicalize-query-params
      (authorize/wrap-require! {:user true})))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
