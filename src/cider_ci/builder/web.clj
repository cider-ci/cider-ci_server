; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.web
  (:require
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.builder.jobs :as jobs]
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.status :as status]
    [cider-ci.utils.routing :as routing]

    [clojure.data.json :as json]
    [clojure.walk :refer [keywordize-keys]]
    [compojure.core :as cpj]
    [ring.middleware.json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))



(defn top-handler [request]
  (logging/warn "HTTP 404 for " request)
  {:status 404
   :body "This resource is not known to the Cider-Ci Builder."
   })


;##### jobs #############################################################

(defn create-job [request]
  (try
    (catcher/with-logging {}
      {:status 201
       :body (jobs/create
               (->> request
                    :body
                    keywordize-keys
                    (map (fn [[k,v]]
                           [k, (case k
                                 :tree_id v
                                 v)]))
                    (into {})))})
    (catch Exception e
      {:status 422 :body {:error (.getMessage e)}})))

(defn available-jobs [request]
  (try
    (catcher/with-logging {}
      {:status 200
       :headers {"content-type" "application/json;charset=utf-8"}
       :body (util/json-write-str
               (jobs/available-jobs
                 (-> request :route-params :tree_id)))})
    (catch clojure.lang.ExceptionInfo e
      (case (-> e ex-data :object :status)
        404 {:status 404 :body (-> e ex-data str)}
        (throw e)))
    (catch org.yaml.snakeyaml.parser.ParserException e
      {:status 422
       :body "Failed to parse the YAML file."})
    (catch Exception e
      {:status 500
       :body (str "Server error.\n\n"
                  (thrown/stringify e))})))

(defn wrap-jobs [default-handler]
  (cpj/routes
    (cpj/GET "/jobs/available/:tree_id" request #'available-jobs)
    (cpj/POST "/jobs/" request #'create-job)
    (cpj/POST "/jobs" request #'create-job)
    (cpj/ANY "*" request default-handler)))


;#### the main handler ########################################################

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      top-handler
      wrap-jobs
      status/wrap
      (routing/wrap-prefix context)
      (authorize/wrap-require! {:service true})))


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
;(debug/wrap-with-log-debug #'create-job)
;(debug/debug-ns *ns*)
