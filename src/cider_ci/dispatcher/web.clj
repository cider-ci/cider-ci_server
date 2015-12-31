; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.web
  (:refer-clojure :exclude [sync])
  (:require
    [cider-ci.dispatcher.abort :as abort]
    [cider-ci.dispatcher.retry :as retry]
    [cider-ci.dispatcher.scripts :refer [script-routes]]
    [cider-ci.dispatcher.sync :as sync]
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.dispatcher.web.auth :as web-auth]

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.ring :as ci-utils-ring]
    [cider-ci.utils.runtime :as runtime]

    [clojure.data :as data]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [÷> ÷>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [humanize Humanize]
    ))


;##### update trial ###########################################################

(defn update-trial [request]
  (let [{{id :id} :params data :body} request]
    (web-auth/validate-trial-token! request id)
    (trials/update-trial (assoc (clojure.walk/keywordize-keys data)
                                :id id))
    {:status 200}))


;##### status dispatch ########################################################

(defn status-handler [request]
  (let [rdbms-status (rdbms/check-connection)
        messaging-status (rdbms/check-connection)
        memory-status (runtime/check-memory-usage)
        body (json/write-str {:rdbms rdbms-status
                              :messaging messaging-status
                              :memory memory-status})]
    {:status  (if (and rdbms-status messaging-status (:OK? memory-status))
                200 499 )
     :body body
     :headers {"content-type" "application/json;charset=utf-8"} }))


;#### sync ####################################################################

(defn sync [request]
  (let [executor (:authenticated-executor request)]
    (if-not (:id executor)
      {:status 403}
      (sync/sync executor (:body request)))))


;#### routing #################################################################

(defn abort-job [request]
  (try (let [body (-> request :body)
             id (-> request :params :id)
             params  (if (map? body) body {})]
         (abort/abort-job id params)
         {:status 202})
       (catch clojure.lang.ExceptionInfo e
         (if-let [status (-> e ex-data :status)]
           {:status status
            :body (.getMessage e)}
           {:status 500
            :body (thrown/stringify e)}))
       (catch Throwable th
         {:status 500
          :body (thrown/stringify th)})))


;#### routing #################################################################

(defn retry-and-resume [request]
  (try (let [body (-> request :body)
             id (-> request :params :id)
             params  (if (map? body) body {})]
         (retry/retry-and-resume id params)
         {:status 202})
       (catch clojure.lang.ExceptionInfo e
         (if-let [status (-> e ex-data :status)]
           {:status status
            :body (.getMessage e)}
           {:status 500
            :body (thrown/stringify e)}))
       (catch Throwable th
         {:status 500
          :body (thrown/stringify th)})))

(defn retry-task [request]
  (try (let [body (-> request :body)
             task-id (-> request :params :id)
             params  (if (map? body) body {})
             trial (retry/retry-task task-id params)]
         {:status 200
          :body (json/write-str trial)
          :headers {"content-type" "application/json;charset=utf-8"}})
       (catch clojure.lang.ExceptionInfo e
         (if-let [status (-> e ex-data :status)]
           {:status status
            :body (.getMessage e)}
           {:status 500
            :body (thrown/stringify e)}))
       (catch Throwable th
         {:status 500
          :body (thrown/stringify th)})))


;#### routing #################################################################

(def routes
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/POST "/jobs/:id/abort" _ #'abort-job)
    (cpj/POST "/jobs/:id/retry-and-resume" _ #'retry-and-resume)
    (cpj/POST "/tasks/:id/retry" _ #'retry-task)
    (cpj/PATCH "/trials/:id" _ update-trial)
    (cpj/ANY "/trials/:id/scripts/*" _ script-routes)
    (cpj/GET "/" [] "OK")
    (cpj/POST "/sync" _ #'sync)
    ))

(defn build-main-handler [context]
  (÷> (wrap-handler-with-logging :debug)
      (cpj.handler/api routes)
      routing/wrap-shutdown
      (ring.middleware.json/wrap-json-body {:keywords? true})
      (routing/wrap-prefix context)
      (authorize/wrap-require! {:executor true :service true})
      (http-basic/wrap {:executor true :service true})
      ci-utils-ring/wrap-webstack-exception
      ))


;#### the server ##############################################################

(defn initialize []
  (let [http-conf (-> (get-config) :services :dispatcher :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))



;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'update-trial)
