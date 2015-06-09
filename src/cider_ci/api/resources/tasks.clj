; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.tasks
  (:require 
    [cider-ci.api.pagination :as pagination]
    [cider-ci.api.util :as util]
    [drtom.logbug.debug :as debug]
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
    ))

(defonce conf (atom nil))

;### get tasks ##################################################################
(defn build-tasks-base-query [job-id]
  (-> (hh/select :tasks.id :tasks.name)
      (hh/modifiers :distinct)
      (hh/from :tasks)
      (hh/where [:= :tasks.job_id job-id])
      (hh/order-by [:tasks.name :desc] [:tasks.id :desc])))


(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (-> query
        (hh/merge-where [:= :tasks.state state]))
    query))

(defn tasks-data [job-id query-params]
  (let [query (-> (build-tasks-base-query job-id)
                  (filter-by-state query-params)
                  (pagination/add-offset-for-honeysql query-params)
                  hc/format)]
    (logging/debug {:query query})
    (jdbc/query (rdbms/get-ds) query)))

(defn get-tasks [request] 
  {:body 
   {:tasks
    (tasks-data (-> request :params :job_id)
                (-> request :query-params))}})


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/jobs/:job_id/tasks/" request (get-tasks request))
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
