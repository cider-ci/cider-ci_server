; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.resources.execution
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
    ))

(defonce conf (atom nil))



;### get-execution-stats ########################################################

(defn get-execution-stats [request]
  (let [id (-> request :params :id)
        data (first (jdbc/query 
                      (rdbms/get-ds) ["SELECT * from execution_stats 
                                      WHERE execution_id = ?" id]))]
    {:data data}))


;### get-execution ##############################################################

(defn query-exeuction [id]
  (first (jdbc/query (rdbms/get-ds) 
                     ["SELECT * from executions
                      WHERE id = ?" id])))

(defn execution-data [params]
  (let [id (:id params)
        execution (query-exeuction id)]
    (dissoc execution 
            :expanded_specification_id
            :specification_id
            )))

(defn get-execution [request] 
  {:body (execution-data (:params request))
   })

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


