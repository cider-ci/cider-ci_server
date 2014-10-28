; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.tasks
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
    [sqlingvo.core]
    ))

(defonce conf (atom nil))

;### get tasks ##################################################################
(defn build-tasks-base-query [execution-id]
  (select (distinct [:tasks.id :tasks.name :tasks.updated_at])
          (from :tasks)
          (where `(= :execution_id ~(uuid execution-id)) :and)
          (order-by (asc :tasks.name))
          (limit 10)))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (compose query (where `(= :tasks.state ~state) :and))
    query))

(defn tasks-data [execution-id query-params]
  (let [query (-> (build-tasks-base-query execution-id)
                  (filter-by-state query-params)
                  (add-offset query-params)
                  sql)
        task-ids (map :id (jdbc/query (rdbms/get-ds) query))]
    {:_links (conj {:self {:href (str (tasks-path execution-id) "?"
                                      (http/build-url-query-string query-params))}
                    :cici:task (map task-link task-ids)}
                   (next-and-previous-link-map (tasks-path execution-id)  
                                               query-params (seq task-ids))
                   (execution-link-map execution-id)
                   (curies-link-map)
                   )}))
  
(defn get-tasks [request] 
  {:hal_json_data (tasks-data (-> request :params :execution_id)
                              (-> request :query-params))})


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/execution/:execution_id/tasks" request (get-tasks request))
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
