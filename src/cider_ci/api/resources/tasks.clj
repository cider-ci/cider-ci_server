; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.tasks
  (:require
    [cider-ci.api.pagination :as pagination]
    [cider-ci.api.util :as util]
    [logbug.debug :as debug]
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
    [honeysql.sql :refer :all]
    ))

;##############################################################################

(def base-query
  (-> (sql-select :tasks.id)
      (sql-from :tasks)
      (sql-order-by [:tasks.created_at :asc] [:tasks.id :asc])))

(defn filter-by-job-id [query query-params]
  (if-let [job-id (:job-id query-params)]
    (-> query
        (sql-merge-where [:= :tasks.job_id job-id]))
    query))

(defn filter-by-state [query query-params]
  (if-let [state (:state query-params)]
    (-> query
        (sql-merge-where [:= :tasks.state state]))
    query))

(defn filter-by-task-specification-id [query query-params]
  (if-let [task-specification-id (:task-specification-id query-params)]
    (-> query
        (sql-merge-where [:= :tasks.task_specification_id
                          task-specification-id]))
    query))

(defn- tasks-query [query-params]
  (-> base-query
      (filter-by-job-id query-params)
      (filter-by-state query-params)
      (filter-by-task-specification-id query-params)
      (pagination/add-offset-for-honeysql query-params)
      sql-format))

(defn- tasks [query-params]
    (jdbc/query (rdbms/get-ds) (tasks-query query-params)))

(defn- get-tasks [request]
  {:body {:tasks (tasks (:query-params request))}})


;### routes #####################################################################

(def routes
  (cpj/routes
    (cpj/GET "/tasks/" request (get-tasks request))
    ))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
