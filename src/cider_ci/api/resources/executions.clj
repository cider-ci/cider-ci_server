; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.executions
  (:require 
    [cider-ci.api.pagination :as pagination]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    ) 
  (:use 
    [clojure.walk :only [keywordize-keys]]
    ))

(defonce conf (atom nil))


(defn dedup-join [honeymap]
  (assoc honeymap :join
         (reduce #(let [[k v] %2] (conj %1 k v)) []
                 (clojure.core/distinct (partition 2 (:join honeymap))))))


(def ^:dynamic inval nil)
;(type (first inval))
;(distinct inval)
;(seq (set inval))
(defn dedup-join-with-logging [honeymap]
  (logging/debug {:type (type honeymap) :honeymap honeymap})
  (let [join (:join honeymap)]
    (logging/debug {:type (type join) :join join})
    (let [partitioned-join (into [] (partition 2 join))]
      (logging/debug {:type (type partitioned-join) :partitioned-join partitioned-join})
      (def inval partitioned-join)
      (let [distinct-joins (clojure.core/distinct partitioned-join)]
        (logging/debug {:distinct-joins distinct-joins})
        (let [reduced-joins (seq (reduce #(let [[k v] %2] (conj %1 k v)) [] distinct-joins))]
          (logging/debug {:reduced-joins reduced-joins})
          (let [res-honeymap (assoc honeymap :join reduced-joins)]
            (logging/debug {:res-honeymap res-honeymap})
            res-honeymap))))))

;### get-index ##################################################################

(defn build-executions-base-query []
  (-> (hh/select :executions.id :executions.created_at)
      (hh/from :executions)
      (hh/modifiers :distinct)
      (hh/order-by [:executions.created_at :desc])
      ))


(defn filter-by-branch [query params]
  (if-let [branch-name (:branch params)]
    (-> query
        (hh/merge-join :commits [:= :commits.tree_id :executions.tree_id])
        (hh/merge-join :branches [:= :branches.current_commit_id :commits.id])
        (hh/merge-where [:= :%lower.branches.name (clojure.string/lower-case branch-name)]))
    query))

(defn filter-by-branch-descendants [query params]
  (if-let [branch-name (:branchdescendants params)]
    (-> query
        (hh/merge-join :commits [:= :commits.tree_id :executions.tree_id])
        (hh/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (hh/merge-join [:branches :branches_via_branches_commits] [:= :branches_via_branches_commits.id :branches_commits.branch_id])
        (hh/merge-where [:= :%lower.branches_via_branches_commits.name (clojure.string/lower-case branch-name)]))
    query))

(defn filter-by-repository [query params]
  (if-let [repository-name (:repository params)]
    (-> query
        (hh/merge-join :commits [:= :commits.tree_id :executions.tree_id])
        (hh/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
        (hh/merge-join [:branches :branches_via_branches_commits] [:= :branches_via_branches_commits.id :branches_commits.branch_id])
        (hh/merge-join :repositories [:= :repositories.id :branches_via_branches_commits.repository_id])
        (hh/merge-where [:= :%lower.repositories.name (clojure.string/lower-case repository-name)]))
    query))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (-> query
        (hh/merge-where [:= :executions.state state]))
    query))

(defn filter-by-tree-id [query params]
  (if-let [tree-id (:treeid params)]
    (-> query
        (hh/merge-where [:= :executions.tree_id tree-id]))
    query))

(defn log-debug-honeymap [honeymap]
  (logging/debug {:honeymap honeymap})
  honeymap)

(defn index [query-params]
  (let [query (-> (build-executions-base-query) 
                  log-debug-honeymap
                  (pagination/add-offset-for-honeysql query-params)
                  log-debug-honeymap
                  (filter-by-state query-params)
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
                  log-debug-honeymap
                  )
        _ (logging/debug "GET /executions " {:query query})]
    (jdbc/query (rdbms/get-ds) query)))

(defn get-index [request] 
  {:body {:executions (index (:query-params request))}})

;### routes #####################################################################
(def routes
  (cpj/routes
    (cpj/GET "/executions/" request (get-index request))))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
