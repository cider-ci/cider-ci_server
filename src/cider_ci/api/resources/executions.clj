; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.executions
  (:require 
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
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    [sqlingvo.core :as sqling]
    ) 
  (:refer-clojure :exclude [distinct group-by])
  (:use 
    [cider-ci.api.resources.shared :exclude [initialize]]
    [clojure.walk :only [keywordize-keys]]
    [sqlingvo.core]
    ))

(defonce conf (atom nil))


;### get-index ##################################################################

(defn build-executions-base-query []
  (select (distinct [:executions.id :executions.created_at])
          (from :executions)
          (join :commits '(on (= :commits.tree_id :executions.tree_id)) :type :left)
          (join :branches_commits '(on (= :branches_commits.commit_id :commits.id)) :type :left)
          (join :branches '(on (= :branches.id :branches_commits.branch_id)) :type :left)
          (join :repositories '(on (= :branches.repository_id :repositories.id)) :type :left)
          ;(join (as :branches :branch_heads) '(on (= :branch_heads.current_commit_id :commits.id)) :type :left)
          (order-by (desc :executions.created_at))
          (limit 10)))

(defn filter-by-branch-name [query params]
  (if-let [branch-name (:branch-name params)]
    (compose query (where `(= :branches.name ~branch-name) :and))
    query))

(defn filter-by-repository-name [query params]
  (if-let [repository-name (:repository-name params)]
    (compose query (where `(= :repositories.name ~repository-name) :and))
    query))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (compose query (where `(= :executions.state ~state) :and))
    query))

(defn filer-branch-heads [query params]
  (if (contains?  params :branch-heads-only )
    (compose query (where '(= :branches.current_commit_id :commits.id) :and))
    query))


(defn executions-data [params]
  (let [query (-> (build-executions-base-query) 
                  (add-offset params)
                  (filter-by-branch-name params)
                  (filter-by-repository-name params)
                  (filter-by-state params)
                  (filer-branch-heads params)
                  sql)
        _ (logging/debug "GET /executions " {:query query})
        execution-ids (map :id (jdbc/query (rdbms/get-ds) query))]
    {:_links (conj {:self {:href (str (executions-path) "?" 
                                       (http/build-url-query-string params))}}
                   (curies-link-map)
                   (next-and-previous-link-map (executions-path) 
                                               params (seq execution-ids))
                   (root-link-map)
                   {:cici:execution (map execution-link execution-ids)}
                   )}))

(defn get-index [request] 
  {:hal_json_data (executions-data (:query-params request) )})

;### routes #####################################################################

(def routes
  (cpj/routes
    (cpj/GET "/executions" request (get-index request))))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
