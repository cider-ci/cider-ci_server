; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.push]

    [cider-ci.utils.status :as status]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as auth.session]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.ring]

    [compojure.core :as cpj]
    [ring.middleware.cookies]
    [ring.middleware.json]
    [ring.middleware.params]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]

    ))

(defn dead-end-handler [req]
  {:status 404
   :body "Not found!"})

(def routes
  (cpj/routes
    (cpj/ANY "*" [] dead-end-handler)
    ))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      routes
      cider-ci.server.push/wrap
      (http-basic/wrap {:service true :user true})
      (auth.session/wrap :anti-forgery true)
      ;cider-ci.utils.ring/wrap-keywordize-request
      ring.middleware.json/wrap-json-response
      ring.middleware.cookies/wrap-cookies
      (ring.middleware.json/wrap-json-body {:keywords? true})
      ring.middleware.params/wrap-params
      status/wrap
      (routing/wrap-prefix context)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
