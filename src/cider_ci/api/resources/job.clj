; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.resources.job
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



;### get-job-stats ########################################################

(defn get-job-stats [request]
  (let [id (-> request :params :id)
        data (first (jdbc/query 
                      (rdbms/get-ds) ["SELECT * from job_stats 
                                      WHERE job_id = ?" id]))]
    {:data data}))


;### get-job ##############################################################

(defn query-exeuction [id]
  (first (jdbc/query (rdbms/get-ds) 
                     ["SELECT * from jobs
                      WHERE id = ?" id])))

(defn job-data [params]
  (let [id (:id params)
        job (query-exeuction id)]
    (dissoc job 
            :expanded_specification_id
            :specification_id
            )))

(defn get-job [request] 
  {:body (job-data (:params request))
   })

;### routes #####################################################################

(def routes 
  (cpj/routes
    (cpj/GET "/job/:id" request (get-job request))
    (cpj/GET "/job/:id/stats" request (get-job-stats request))))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)


