; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.web
  (:require 
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.utils.with :as with]
    [cider-ci.api.resources :as resources]
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
    [ring.middleware.resource :as resource]
    ))

(defonce conf (atom nil))



;##### non public resources authentication #################################### 

(defn return-authenticate! [request]
  {:status 401
   :headers 
   {"WWW-Authenticate" 
    "Basic realm=\"Cider-CI; sign in or provide credentials\""}
   })

(defn authenticate-non-public [request handler] 
  (cond
    (:authenticated-user request) (handler request)
    (:authenticated-application request) (handler request) 
    :else (return-authenticate! request)))

    
(defn wrap-authenticate-non-public [handler]
  (fn [request]
    (authenticate-non-public request handler)))


;##### static resources ####################################################### 

(defn static-resources-handler [request]
  (let [context (:context request)
        context-lenth (count context)
        wo-prefix-request (assoc request
                                 :uri (subs (:uri request) context-lenth))]
    (logging/debug wo-prefix-request)
    (resource/resource-request wo-prefix-request "")))

(defn wrap-static-resources-dispatch [default-handler]
  (cpj/routes
    (cpj/ANY "/doc*" request static-resources-handler)
    (cpj/ANY "/api-browser*" request static-resources-handler)
    (cpj/ANY "*" request default-handler)))


;##### routes and wrappers #################################################### 

(defn wrap-debug-logging [handler]
  (fn [request]
    (let [wrap-debug-logging-level (or (:wrap-debug-logging-level request) 0 )]
      (logging/debug "wrap-debug-logging " wrap-debug-logging-level " request: " request)
      (let [response (handler (assoc request :wrap-debug-logging-level (+ wrap-debug-logging-level 1)))]
        (logging/debug  "wrap-debug-logging " wrap-debug-logging-level " response: " response)
        response))))

(defn wrap-catch-exception [handler]
  (fn [request]
    (with/logging
      (handler request))))

(defn wrap-prefix [default-handler prefix]
  (cpj/routes
    (cpj/context prefix []
                 (cpj/ANY "*" request default-handler))
    (cpj/ANY "*" [] {:status 404})))

(defn build-main-handler [context]
  ( -> (resources/build-routes-handler)
       (wrap-debug-logging)
       (ring.middleware.json/wrap-json-params)
       (wrap-debug-logging)
       (ring.middleware.params/wrap-params)
       (wrap-debug-logging)
       (wrap-authenticate-non-public)
       (wrap-debug-logging)
       (http-basic/wrap)
       (wrap-debug-logging)
       (session/wrap)
       (wrap-debug-logging)
       (cookies/wrap-cookies)
       (wrap-debug-logging)
       (wrap-static-resources-dispatch)
       (wrap-debug-logging)
       (wrap-prefix context)
       (wrap-debug-logging)
       (wrap-catch-exception)))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (let [context (str (:context (:web @conf)) (:sub_path (:web @conf)))]
    (http-server/start @conf (build-main-handler context))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
