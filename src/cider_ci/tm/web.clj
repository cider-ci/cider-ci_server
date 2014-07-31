; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.web
  (:require 
    [cider-ci.tm.trial :as trial]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
    [ring.middleware.json]
    ))


(defonce conf (atom nil))

;##### update trial ########################################################### 
 
(defn update-trial [id params]
  (trial/update id (clojure.walk/keywordize-keys params))
  {:status 200})


;##### routes and handlers #################################################### 

(defn log-handler [handler level]
  (fn [request]
    (logging/debug "log-handler " level " request: " request)
    (let [response (handler request)]
      (logging/debug  "log-handler " level " response: " response)
      response)))

(defn build-routes [context]
  (cpj/routes 
    (cpj/context context []

                 (cpj/PATCH "/trials/:id" 
                            {{id :id} :params json-params :json-params} 
                            (update-trial id json-params))

                 (cpj/GET "/" [] "OK")

                 )))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (log-handler 2)
       (ring.middleware.json/wrap-json-params)
       (log-handler 1)
       (http/authenticate)
       (log-handler 0)))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (http-server/start @conf (build-main-handler)))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

