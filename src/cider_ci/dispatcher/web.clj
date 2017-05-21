; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.web
  (:require
    [cider-ci.dispatcher.abort :as abort]
    [cider-ci.dispatcher.retry :as retry]
    [cider-ci.dispatcher.web.executor :as web.executor]

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.ring :as ci-utils-ring]
    [cider-ci.utils.status :as status]
    [cider-ci.utils.shutdown :as shutdown]

    [clojure.data :as data]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [humanize Humanize]
    ))



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
    (cpj/POST "/jobs/:id/abort" _ #'abort-job)
    (cpj/POST "/jobs/:id/retry-and-resume" _ #'retry-and-resume)
    (cpj/POST "/tasks/:id/retry" _ #'retry-task)
    (cpj/GET "/" [] "OK")
    ))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      (cpj.handler/api routes)
      status/wrap
      shutdown/wrap
      (authorize/wrap-require! {:service true})
      (http-basic/wrap {:service true})
      web.executor/wrap-dispatch-executor-routes
      (routing/wrap-prefix context)
      ci-utils-ring/wrap-webstack-exception))


;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'update-trial)
