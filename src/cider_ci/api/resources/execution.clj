; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.resources.execution
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
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



;### get-execution-stats ########################################################

(defn get-execution-stats [request]
  (let [id (-> request :params :id)
        data (first (jdbc/query 
                      (:ds @conf) ["SELECT * from execution_stats 
                                   WHERE execution_id = ?::uuid" id]))
        links {:_links 
               (conj {}
                     (curies-link-map)
                     (execution-link-map id)
                     (root-link-map))}]
    {:hal_json_data (conj data links)}))


;### get-execution ##############################################################

(defn query-exeuction [id]
  (first (jdbc/query (:ds @conf) 
                     ["SELECT * from executions
                      WHERE id = ?::UUID" id])))

(defn execution-data [params]
  (let [id (:id params)
        execution (query-exeuction id)]
    (assoc 
      (dissoc execution :substituted_specification_data :specification_id)
      :_links (conj 
                { :self (execution-link id)}
                (execution-stats-link-map id)
                (tasks-link-map id)
                (root-link-map)
                (tree-attachments-link-map id)
                (curies-link-map)
                ))))

(defn get-execution [request] 
  {:hal_json_data  (execution-data (:params request))})


;### routes #####################################################################

(def routes 
  (cpj/routes
    (cpj/GET "/execution/:id" request (get-execution request))
    (cpj/GET "/execution/:id/stats" request (get-execution-stats request))))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
