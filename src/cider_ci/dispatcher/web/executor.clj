; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
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
    [cider-ci.auth.http-basic :as http-basic]
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


;#### sync ####################################################################
; the sync route bypasses authentication for an existing executor, the auth
; properties are evaluated and an executor is created on the fly if it doesn't
; exist yet

(defn find-or-create-executor [executor-name]
  (or (first (jdbc/query
               (rdbms/get-ds)
               ["SELECT * FROM executors WHERE name = ? OR id::text = ?"
                executor-name executor-name]))
      (first (jdbc/insert!
               (rdbms/get-ds)
               :executors {:name executor-name}))))

(defn sync [request]
  (let [request-with-auth-properties (http-basic/extract-and-add-basic-auth-properties
                                       request)
        {username :username password :password} (:basic-auth-request
                                                  request-with-auth-properties)]
    (if-not (http-basic/password-matches? password username)
      {:status 403 :body "Password missmatch."}
      (if-let [executor (find-or-create-executor username)]
        (sync/sync executor (:body request))
        {:status 422 :body "Executor could not be created"}))))


;#### routes ##################################################################

(def executor-routes
  (-> (cpj/routes
        (cpj/PATCH "/trials/:id" _ update-trial)
        (cpj/ANY "/trials/:id/scripts/*" _ script-routes))
      (authorize/wrap-require! {:executor true})
      (http-basic/wrap {:executor true})))

(defn wrap-dispatch-executor-routes [default-handler]
  (I> wrap-handler-with-logging
      (cpj/routes
        (cpj/ANY "/trials/*" _ executor-routes)
        (cpj/POST "/sync" _ #'sync)
        (cpj/ANY "*" _ default-handler))))

;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'update-trial)
