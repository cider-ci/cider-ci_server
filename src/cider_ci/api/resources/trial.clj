; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.trial
  (:require
    [cider-ci.api.util :as util]
    [logbug.debug :as debug]
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

;### get trial ##################################################################

(defn get-trial [request]
  (logging/debug request)
  (let [trial-id (-> request :params :id)
        trial (first (jdbc/query (rdbms/get-ds) ["SELECT * from trials WHERE id = ?" trial-id]))]
    (when trial
      {:body (dissoc trial :token)})))



;### routes #####################################################################

(def routes
  (cpj/routes
    (cpj/GET "/trials/:id" request (get-trial request))
    ))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
