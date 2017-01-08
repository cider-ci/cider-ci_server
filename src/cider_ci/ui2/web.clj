; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ui2.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.session.be :as session]

    [cider-ci.ui2.web.shared :as web.shared :refer [dynamic]]
    [cider-ci.ui2.create-admin.be :as create-admin]
    [cider-ci.ui2.welcome-page.be :as welcome-page]
    [cider-ci.ui2.root :as root]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as auth.session]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.shutdown :as shutdown]
    [cider-ci.utils.status :as status]

    [clojure.walk :refer [keywordize-keys]]
    [clj-time.core :as time]
    [clojure.data :as data]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.accept]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.defaults]
    [ring.middleware.json]
    [ring.middleware.params]
    [ring.util.response :refer [charset]]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))



(def routes
  (cpj/routes
    (cpj/GET "/" [] #'dynamic)
    (cpj/GET "/initial-admin" [] #'dynamic)
    (cpj/GET "/debug" [] #'dynamic)
    (cpj/GET "/*" [] #'dynamic)
    ))

(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept
    handler
    {:mime
     ["application/json-roa+json" :qs 1 :as :json-roa
      "application/json" :qs 1 :as :json
      "text/html" :qs 1 :as :html
      ]}))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      routes
      create-admin/wrap ;must come after public is served
      welcome-page/wrap
      shutdown/wrap
      ; authentication and primitive authorization
      (http-basic/wrap {:service true :user true})
      (auth.session/wrap :anti-forgery true)
      ; unauthenticated routes here
      session/wrap-routes
      cider-ci.utils.ring/wrap-keywordize-request
      cookies/wrap-cookies
      (ring.middleware.json/wrap-json-body {:keywords? true})
      ring.middleware.json/wrap-json-response
      ring.middleware.params/wrap-params
      (ring.middleware.defaults/wrap-defaults {:static {:resources "public"}})
      wrap-accept
      status/wrap
      (routing/wrap-prefix context)
      routing/wrap-exception))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns 'cider-ci.auth.anti-forgery)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)

