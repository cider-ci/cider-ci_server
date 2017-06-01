; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication
  (:require
    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authentication.basic-header :as basic-header]
    [cider-ci.auth.authentication.guest :as guest]
    [cider-ci.auth.authentication.password :as password]
    [cider-ci.auth.authentication.session :as session]
    [cider-ci.auth.authentication.executor :as executor]
    [cider-ci.auth.authentication.token :as token]
    [cider-ci.auth.authentication.token-header :as token-header]
    [cider-ci.auth.authentication.system-admin :as system-admin]

    [compojure.core :as cpj]
    [ring.middleware.cookies]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))

(defn- add-www-auth-header-if-401 [response]
  (case (:status response)
    401 (assoc-in response [:headers "WWW-Authenticate"]
                  (str "Basic realm=\"Cider-CI, authenticate "
                       "with password or token\""))
    response))

(defn authenticate [request handler]
  (-> request handler add-www-auth-header-if-401))

(defn authenticated-entity [request]
  (if-let [entity (:authenticated-entity request)]
    {:status 200
     :body {:authenticated-entity entity}}
    {:status 404
     :body "no entity has been authenticated"}))

(defn wrap-entity-route [handler]
  (cpj/routes
    (cpj/GET "/authenticated-entity" _ authenticated-entity)
    (cpj/ANY "*" _ handler)))

(defn wrap [handler]
  (fn [request]
    (authenticate request
                  (I> wrap-handler-with-logging
                      handler
                      anti-forgery/wrap
                      wrap-entity-route
                      guest/wrap
                      session/wrap-authenticate
                      password/wrap-authenticate
                      token/wrap-authenticate
                      executor/wrap-authenticate
                      system-admin/wrap-authenticate
                      token-header/wrap-extract
                      basic-header/wrap-extract
                      ring.middleware.cookies/wrap-cookies
                      ))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
