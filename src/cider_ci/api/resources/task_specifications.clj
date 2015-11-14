; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.task-specifications
  (:require
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
    ))

(defonce conf (atom nil))

;### get tasks ##################################################################
(defn- get-spec [request]
  {:body
   (first (jdbc/query (rdbms/get-ds)
                      ["SELECT * FROM task_specifications WHERE id = ? "
                       (-> request :route-params :id)]))
   })

;### routes #####################################################################
(def routes
  (cpj/routes
    (cpj/GET "/task-specifications/:id" _ get-spec)
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
