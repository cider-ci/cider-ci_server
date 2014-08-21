; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.trial
  (:require 
    [cider-ci.utils.debug :as debug]
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

;### get trial ##################################################################

(defn get-trial [request]
  (logging/debug request)
  (let [trial-uuid (-> request :params :id uuid)
        trial (first (jdbc/query (:ds @conf) ["SELECT * from trials WHERE id = ?" trial-uuid]))]
    {:hal_json_data (conj trial
                          {:_links 
                           (conj {:self (trial-link trial-uuid)}
                                 (root-link-map)
                                 (task-link-map (:task_id trial))
                                 (curies-link-map)
                                 (trials-link-map (:task_id trial))
                                 (trial-attachments-link-map trial-uuid)
                                 )})}))



;### routes #####################################################################

(def routes 
  (cpj/routes
    (cpj/GET "/trial/:id" request (get-trial request))
    ))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
