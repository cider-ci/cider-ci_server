; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.web.executor
  (:refer-clojure :exclude [sync])
  (:require
    [cider-ci.dispatcher.scripts :refer [script-routes]]
    [cider-ci.dispatcher.sync :as sync]
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.dispatcher.web.auth :as web-auth]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]


    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))



;##### update trial ###########################################################

(defn update-trial [request]
  (let [{{id :id} :params data :body} request]
    (web-auth/validate-trial-token! request id)
    (trials/update-trial (assoc (clojure.walk/keywordize-keys data)
                                :id id))
    {:status 200}))

(defn sync [request]
  (if-let [executor (:authenticated-entity request)]
    (sync/sync executor (:body request))
    {:status 422 :body "Executor could not be created"}))


;#### routes ##################################################################

(def executor-routes
  (-> (cpj/routes
        (cpj/PATCH "/trials/:id" _ update-trial)
        (cpj/ANY "/trials/:id/scripts/*" _ script-routes))))

(defn wrap-dispatch-executor-routes [default-handler]
  (I> wrap-handler-with-logging
      (cpj/routes
        (cpj/ANY "/trials/*" _
                 (authorize/wrap-require! executor-routes {:executor true}))
        (cpj/POST "/sync" _
                  (authorize/wrap-require! #'sync {:executor true}))
        (cpj/ANY "*" _ default-handler))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'update-trial)
