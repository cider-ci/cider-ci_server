; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.ui2.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.ui2.constants :refer [CONTEXT]]
    [cider-ci.server.ui2.session.be :as session]

    [cider-ci.server.ui2.web.shared :as web.shared :refer [dynamic]]
    [cider-ci.server.ui2.welcome-page.be :as welcome-page]
    [cider-ci.server.ui2.root :as root]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing :as routing]
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
    (cpj/ANY "/session*" [] #'session/routes)
    (cpj/GET "/" [] #'dynamic)
    (cpj/GET "/initial-admin" [] #'dynamic)
    (cpj/GET "/debug" [] #'dynamic)
    (cpj/GET "/*" [] #'dynamic)
    ))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      routes
      welcome-page/wrap
      ; authentication and primitive authorization
      cider-ci.utils.ring/wrap-keywordize-request
      cookies/wrap-cookies
      ring.middleware.params/wrap-params
      (ring.middleware.defaults/wrap-defaults {:static {:resources "public"}})
      status/wrap
      (routing/wrap-prefix context)
      routing/wrap-exception))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.anti-forgery)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
