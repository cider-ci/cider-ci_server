; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.web
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.builder.jobs :as jobs]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [ring.middleware.json]
    [clojure.walk :refer [keywordize-keys]]
    ))


(defn top-handler [request]
  {:status 418})


;##### status dispatch ######################################################## 

(defn status-handler [request]
  (let [stati {:rdbms (rdbms/check-connection)
               :messaging (messaging/check-connection)
               }]
    (if (every? identity (vals stati))
      {:status 200
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} }
      {:status 511
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} })))

(defn wrap-status-dispatch [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))


;##### jobs ############################################################# 

(defn create-job [request]
  (with/log :warn
    {:status 201 
     :body (jobs/create 
             (->> request
                  :body
                  keywordize-keys
                  (map (fn [[k,v]]
                         [k, (case k
                               :tree_id v
                               v)]))
                  (into {})))}))

(defn available-jobs [request]
  (try 
    {:status 200 
     :headers {"content-type" "application/json;charset=utf-8"}
     :body (json/write-str 
             (jobs/available-jobs 
               (-> request :route-params :tree_id)))}
    (catch clojure.lang.ExceptionInfo e
      (case (-> e ex-data :object :status)
        404 {:status 404}
        (throw e)))
    (catch org.yaml.snakeyaml.parser.ParserException e
      {:status 422
       :body "Failed to parse the YAML file."}
      )))

(defn wrap-jobs [default-handler]
  (cpj/routes
    (cpj/GET "/jobs/available/:tree_id" request #'available-jobs)
    (cpj/POST "/jobs/" request #'create-job)
    (cpj/POST "/jobs" request #'create-job)
    (cpj/ANY "*" request default-handler)))


;#### the main handler ########################################################

(defn build-main-handler [context]
  ( -> top-handler
       wrap-status-dispatch
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       wrap-jobs
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       ring.middleware.json/wrap-json-response
       ring.middleware.json/wrap-json-body
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       (auth/wrap-authenticate-and-authorize-service)
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       (routing/wrap-prefix context)
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       (http-basic/wrap {:service true})
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       ))


;#### the server ##############################################################

(defn initialize []
  (let [http-conf (-> (get-config) :services :builder :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns 'cider-ci.auth.core)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
