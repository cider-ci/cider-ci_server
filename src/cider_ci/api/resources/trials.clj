; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.trials
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

;### get-trials #################################################################
(defn build-trials-base-query [task-id]
  (-> (hh/from :trials)
      (hh/select :trials.id :trials.created_at)
      (hh/modifiers :distinct)
      (hh/where [:= :trials.task_id task-id])
      (hh/order-by [:trials.created_at :asc] [:trials.id :asc])))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (-> query (hh/merge-where [:= :trials.state state]))
    query))


(defn trials-data [task-id query-params]
  (let [query (-> (build-trials-base-query task-id)
                  (filter-by-state query-params)
                  (pagination/add-offset-for-honeysql query-params)
                  hc/format)]
    (logging/debug {:query query})
    (jdbc/query (rdbms/get-ds) query)))

(defn get-trials  [request]
  {:body {:trials
          (trials-data (-> request :route-params :task_id)
                       (-> request :query-params))}})


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/tasks/:task_id/trials/" request (get-trials request))
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
