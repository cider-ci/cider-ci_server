; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.repository.git.repositories :as git.repositories]
    [cider-ci.server.repository.roa.core :as roa]
    [cider-ci.server.repository.sql.repository :as sql.repository]
    [cider-ci.server.repository.web.ls-tree :as web.ls-tree]
    [cider-ci.server.repository.web.project-configuration :as web.project-configuration]
    [cider-ci.server.repository.web.projects :as web.projects]
    [cider-ci.server.repository.web.push :as web.push]
    [cider-ci.server.repository.web.push-notifications :as push-notifications]
    [cider-ci.server.repository.web.shared :refer :all]
    [cider-ci.server.repository.web.ui :as web.ui]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.routing :as routing]

    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
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

;##### get path content #######################################################

(defn get-path-content [request]
  (logging/debug request)
  (try
    (let [id (-> request :route-params :id)
          path (-> request :route-params :*)]
      (when-let [repository (sql.repository/resolve id)]
        (when-let [content  (git.repositories/get-path-contents repository id path)]
          {:body content})))
    (catch clojure.lang.ExceptionInfo e
      (cond (re-find #"does not exist in"  (str e)) {:status 404 :body (-> e ex-data :err)}
            :else (respond-with-500 request e)))
    (catch Exception e
      (respond-with-500 request e))))


;##### routes #################################################################

(def routes
  (cpj/routes
    (cpj/GET "/project-configuration/:id" _
             (authorize/wrap-require!
               #'web.project-configuration/project-configuration
               {:service true}))
    (cpj/GET "/ls-tree" _
             (authorize/wrap-require! #'web.ls-tree/ls-tree {:service true}))
    (cpj/GET "/path-content/:id/*" _
             (authorize/wrap-require! #'get-path-content {:service true}))
    (cpj/ANY "/projects/*" _ web.projects/routes)))

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
      (cpj.handler/api routes)
      roa/wrap
      web.ui/wrap
      wrap-accept
      web.push/wrap
      (authorize/wrap-require! {:service true :user true})
      cookies/wrap-cookies
      (ring.middleware.defaults/wrap-defaults {:static {:resources "public"}})
      push-notifications/wrap
      (routing/wrap-prefix context)
      routing/wrap-exception))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.anti-forgery)
;(debug/debug-ns *ns*)
