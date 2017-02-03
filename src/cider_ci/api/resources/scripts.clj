; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.scripts
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


(defn- get-script [request]
  {:body
   (->> ["SELECT * FROM scripts WHERE id = ? "
         (-> request :route-params :id)]
        (jdbc/query (rdbms/get-ds))
        first)})

(defn- get-scripts [request]
  {:body {:scripts
          (->> ["SELECT id FROM scripts WHERE trial_id = ? "
                (-> request :route-params :id)]
               (jdbc/query (rdbms/get-ds)))}})


;### routes #####################################################################
(def routes
  (cpj/routes
    (cpj/GET "/trials/:id/scripts/" _ get-scripts)
    (cpj/GET "/scripts/:id" _ get-script)
    ))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
