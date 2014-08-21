; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.tree-attachments
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms.json]
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

;### get-attachments ############################################################
(defn build-attachments-base-query [execution-id]
  (let [tree-id (:tree_id (first (jdbc/query (:ds @conf) ["SELECT tree_id FROM executions WHERE id = ?" execution-id])))]
    (select [:id :path]
            (from :tree_attachments)
            (where `(like :path ~(str "/"tree-id"/%")))
            (order-by (asc :path)))))

(defn attachments-data [execution-id query-params]
  (let [query (-> (build-attachments-base-query execution-id)
                  (add-offset query-params)
                  sql)
        _ (logging/debug query)
        attachment-paths (map :path (jdbc/query (:ds @conf) query)) ]
    {:_links (conj {:self (tree-attachments-link execution-id)}
                   (curies-link-map)
                   (execution-link-map execution-id)
                   (next-and-previous-link-map (tree-attachments-path execution-id)
                                               query-params (seq attachment-paths))
                   {:cici:tree-attachment
                    (map tree-attachment-link attachment-paths)})}))

(defn get-attachments [request]
  (let [execution-id (-> request :route-params :execution_id uuid)
        query-params (-> request :query-params)]
    {:hal_json_data (attachments-data execution-id query-params)}))


;### get-attachment #############################################################
(defn get-attachment [request]
  (let [path (-> request :route-params :*)
        tree-id (second (re-matches #"/([^\/]+)/.*" path))
        attachment (first (jdbc/query 
                            (:ds @conf) 
                            ["SELECT * from tree_attachments WHERE path = ?" path]))]
    {:hal_json_data (conj attachment
                          {:_links (conj {:self (tree-attachment-link path)}
                                         (curies-link-map)
                                         {:data-stream 
                                          {:href (http/build-url (:storage_manager_server @conf)
                                                                 (str "/storage/tree-attachments" path))
                                           :title "Data-Stream"}}
                                         )})}))


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/execution/:execution_id/tree-attachments" request (get-attachments request))
    (cpj/GET "/tree-attachment*" request (get-attachment request))
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
