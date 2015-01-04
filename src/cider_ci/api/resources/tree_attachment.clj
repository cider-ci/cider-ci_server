; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.tree-attachment
  (:require 
    [cider-ci.api.util :as util]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    ))



;### get-attachment #############################################################

(defn query-attachment [id]
  (first (jdbc/query 
           (rdbms/get-ds) 
           ["SELECT * from tree_attachments WHERE id = ?" id])))

(defn get-attachment [request]
  (let [id (-> request :route-params :id)
        attachment (query-attachment id)]
    (when attachment
      {:body attachment})))


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/tree-attachment/:id" request (get-attachment request))
    ))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
