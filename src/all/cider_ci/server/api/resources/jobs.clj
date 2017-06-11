; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.resources.jobs
  (:require
    [cider-ci.server.api.pagination :as pagination]

    [cider-ci.utils.http :as http]
    [cider-ci.utils.http :as utils-http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.server.builder :as builder]


    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:use
    [clojure.walk :only [keywordize-keys]]
    ))

;### general ####################################################################

(defn dedup-join [honeymap]
  (assoc honeymap :join
         (reduce #(let [[k v] %2] (conj %1 k v)) []
                 (clojure.core/distinct (partition 2 (:join honeymap))))))


;### get-index ##################################################################

(defn build-jobs-base-query []
  (-> (hh/select :jobs.id :jobs.created_at)
      (hh/from :jobs)
      (hh/modifiers :distinct)
      (hh/order-by [:jobs.created_at :desc])
      ))

(defn filter-by-branch [query params]
  (if-let [branch-name (:branch_head params)]
    (-> query
        (hh/merge-join :commits [:= :commits.tree_id :jobs.tree_id])
        (hh/merge-join :branches [:= :branches.current_commit_id :commits.id])
        (hh/merge-where [:= :branches.name  branch-name]))
    query))

(defn filter-by-branch-descendants [query params]
  (if-let [branch-name (:branch_descendants params)]
    (-> query
        (hh/merge-join :commits [:= :commits.tree_id :jobs.tree_id])
        (hh/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (hh/merge-join [:branches :branches_via_branches_commits] [:= :branches_via_branches_commits.id :branches_commits.branch_id])
        (hh/merge-where [:= :branches_via_branches_commits.name branch-name]))
    query))

(defn filter-by-repository [query params]
  (if-let [repository-url (:repository_url params)]
    (-> query
        (hh/merge-join :commits [:= :commits.tree_id :jobs.tree_id])
        (hh/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (hh/merge-join [:branches :branches_via_branches_commits] [:= :branches_via_branches_commits.id :branches_commits.branch_id])
        (hh/merge-join :repositories [:= :repositories.id :branches_via_branches_commits.repository_id])
        (hh/merge-where [:= :repositories.git_url repository-url]))
    query))

(defn filter-by-job-specification-id [query params]
  (if-let [job-specification-id (:job_specification_id params)]
    (-> query
        (hh/merge-where [:= :jobs.job_specification_id job-specification-id]))
    query))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (-> query
        (hh/merge-where [:= :jobs.state state]))
    query))

(defn filter-by-tree-id [query params]
  (if-let [tree-id (:tree_id params)]
    (-> query
        (hh/merge-where [:= :jobs.tree_id tree-id]))
    query))

(defn filter-by-key [query params]
  (if-let [key (:key params)]
    (-> query
        (hh/merge-where [:= :jobs.key key]))
    query))

(defn log-debug-honeymap [honeymap]
  (logging/debug {:honeymap honeymap})
  honeymap)

(defn index [query-params]
  (let [query (-> (build-jobs-base-query)
                  log-debug-honeymap
                  (pagination/add-offset-for-honeysql query-params)
                  log-debug-honeymap
                  (filter-by-key query-params)
                  log-debug-honeymap
                  (filter-by-state query-params)
                  log-debug-honeymap
                  (filter-by-job-specification-id query-params)
                  log-debug-honeymap
                  (filter-by-tree-id query-params)
                  log-debug-honeymap
                  (filter-by-repository query-params)
                  log-debug-honeymap
                  (filter-by-branch-descendants query-params)
                  log-debug-honeymap
                  (filter-by-branch query-params)
                  log-debug-honeymap
                  dedup-join
                  log-debug-honeymap
                  hc/format
                  log-debug-honeymap)
        _ (logging/debug "GET /jobs " {:query query})]
    (jdbc/query (rdbms/get-ds) query)))

(defn get-index [request]
  {:body {:jobs (index (:query-params request))}})


;### create job #################################################################

(defn create-job [request]
  (if-not (= (->> request :body keys set) #{:tree_id  :key})
    {:status 422
     :body {:message "The request body must exactly contain the keys 'tree_id' and 'key'"}}
    (let [user-id (-> request :authenticated-entity :id)
          params (assoc (select-keys (:body request) [:tree_id :key])
                        :created_by user-id)]
      (assert (= (-> request :authenticated-entity :type) :user))
      (if-let [job (builder/create-job params)]
        {:status 201
         :body job}
        (throw (ex-info "Creating job failed " params))))))


;### routes #####################################################################

(def routes
  (cpj/routes
    (cpj/GET "/jobs/" request (get-index request))
    (cpj/POST "/jobs/create" _ create-job)))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
