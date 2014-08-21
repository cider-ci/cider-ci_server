; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.root
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    )
  (:refer-clojure :exclude [get])
  (:use 
    [cider-ci.api.resources.shared :exclude [initialize]]
    ))
    

(defn response-data []
  {:message "Welcome to the Cider-CI API!"
   :_links  (conj {}
                  (root-link-map)
                  (executions-link-map)
                  (curies-link-map)
                  )})


(defn get [request]
  (-> { :hal_json_data (response-data)}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
