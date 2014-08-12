(ns cider-ci.api.resources.trial-attachments
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

;### get-attachment #############################################################
(defn build-attachments-base-query  [trial-id]
  (select [:id :path]
          (from :trial_attachments)
          (where `(like :path ~(str "/" trial-id "%")))
          (order-by (asc :path))))

(defn attachments-data [trial-id query-params]
  (let [query (-> (build-attachments-base-query trial-id)
                  (add-offset query-params)
                  sql)
        _ (logging/debug query)
        attachment-paths (map :path (jdbc/query (:ds @conf) query)) ]
    {:_links (conj {:self (trial-attachments-link trial-id)}
                   (curies-link-map)
                   (trial-link-map trial-id)
                   (next-and-previous-link-map (trial-attachments-path trial-id)
                                               query-params (seq attachment-paths))
                   {:cider-ci_api-docs:trial-attachment
                    (map trial-attachment-link attachment-paths)})}))

(defn get-attachments [request]
  (let [trial-id (-> request :route-params :trial-id uuid)
        query-params (-> request :query-params)]
    {:hal_json_data (attachments-data trial-id query-params)}))


;### get-attachment #############################################################
(defn get-attachment [request]
  (let [path (-> request :route-params :*)
        trial-id (second (re-matches #"/([^\/]+)/.*" path))
        attachment (first (jdbc/query 
                            (:ds @conf) 
                            ["SELECT * from trial_attachments WHERE path = ?" path]))]
    {:hal_json_data (conj attachment
                          {:_links (conj {:self (trial-attachment-link path)}
                                         (trial-attachments-link-map trial-id)
                                         (curies-link-map)
                                         {:data-stream 
                                          {:href (http/build-url (:storage_manager_server @conf)
                                                                 (str "/storage/trial-attachments" path))
                                           :title "Data-Stream"}}
                                         )})}))


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/trial/:trial-id/attachments" request (get-attachments request))
    (cpj/GET "/trial-attachment*" request (get-attachment request))
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)





