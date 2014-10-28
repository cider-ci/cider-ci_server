; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.task
  (:require 
    [cider-ci.utils.debug :as debug]
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

;### get task ###################################################################

(defn get-task [request]
  (logging/debug request)
  (let [uuid (-> request :params :id uuid)
        task (first (jdbc/query (rdbms/get-ds) ["SELECT * from tasks WHERE id = ?" uuid]))]
    (logging/debug uuid task)
    (when task 
      {:hal_json_data (conj task
                            {:_links 
                             (conj {:self (task-link uuid)}
                                   (root-link-map)
                                   (execution-link-map (:execution_id task))
                                   (curies-link-map)
                                   (trials-link-map uuid))})})))


;### routes #####################################################################

(def routes 
  (cpj/routes
    (cpj/GET "/task/:id" request (get-task request))
    ))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
