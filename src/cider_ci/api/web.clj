; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.web
  (:require 
    [cider-ci.api.resources :as resources]
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.cors :as cors]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.content-type :as content-type]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.middleware.resource :as resource]
    [ring.util.response :as response]
    )
  (:use 
    [clojure.walk :only [keywordize-keys]]
    ))


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

;##### status dispatch ######################################################## 

(defn status-handler [request]
  (let [stati {:rdbms (rdbms/check-connection)
               :messaging (messaging/check-connection)
               }]
    (if (every? identity (vals stati))
      {:status 200
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} }
      {:status 511
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} })))

(defn wrap-status-dispatch [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))


;##### routes and wrappers #################################################### 

(defn build-main-handler [context]
  ( -> (resources/build-routes-handler)
       (routing/wrap-debug-logging 'cider-ci.api.web)
       ring.middleware.json/wrap-json-params
       (routing/wrap-debug-logging 'cider-ci.api.web)
       (ring.middleware.params/wrap-params)
       (routing/wrap-debug-logging 'cider-ci.api.web)
       wrap-status-dispatch
       (routing/wrap-debug-logging 'cider-ci.api.web)
       (auth/wrap-authenticate-and-authorize-service-or-user)
       (routing/wrap-debug-logging 'cider-ci.api.web)
       (http-basic/wrap {:user true :service true})
       (routing/wrap-debug-logging 'cider-ci.api.web)
       session/wrap
       (routing/wrap-debug-logging 'cider-ci.api.web)
       cookies/wrap-cookies
       (routing/wrap-debug-logging 'cider-ci.api.web)
       wrap-static-resources-dispatch
       (routing/wrap-debug-logging 'cider-ci.api.web)
       content-type/wrap-content-type
       (routing/wrap-debug-logging 'cider-ci.api.web)
       cors/wrap
       (routing/wrap-debug-logging 'cider-ci.api.web)
       (routing/wrap-prefix context)
       (routing/wrap-debug-logging 'cider-ci.api.web)
       (routing/wrap-log-exception)
       ))


;#### the server ##############################################################

(defn initialize []
  (let [http-conf (-> (get-config) :services :api :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns 'cider-ci.auth.core)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns *ns*)
