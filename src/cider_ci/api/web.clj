; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.web
  (:require
    [cider-ci.api.resources :as resources]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.open-session.cors :as cors]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.status :as status]
    [cider-ci.utils.routing :as routing]


    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.content-type :as content-type]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.middleware.resource :as resource]
    [ring.util.response :as response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
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



;##### routes and wrappers ####################################################

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      (resources/build-routes-handler)
      routing/wrap-shutdown
      ring.middleware.json/wrap-json-params
      ring.middleware.json/wrap-json-params
      (ring.middleware.params/wrap-params)
      status/wrap
      (authorize/wrap-require! {:user true :service true})
      (http-basic/wrap {:user true :service true})
      session/wrap
      cookies/wrap-cookies
      wrap-static-resources-dispatch
      content-type/wrap-content-type
      cors/wrap
      (routing/wrap-prefix context)
      (routing/wrap-log-exception)))


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
