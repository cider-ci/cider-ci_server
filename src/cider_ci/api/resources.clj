; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.resources
  (:require
    [cider-ci.api.json-roa :as json-roa]
    [cider-ci.api.resources.job :as job]
    [cider-ci.api.resources.jobs :as jobs]
    [cider-ci.api.resources.root :as resources.root]
    [cider-ci.api.resources.task :as task]
    [cider-ci.api.resources.tasks :as tasks]
    [cider-ci.api.resources.tree-attachment :as tree-attachment]
    [cider-ci.api.resources.tree-attachments :as tree-attachments]
    [cider-ci.api.resources.trial :as trial]
    [cider-ci.api.resources.trial-attachment :as trial-attachment]
    [cider-ci.api.resources.trial-attachments :as trial-attachments]
    [cider-ci.api.resources.trials :as trials]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.routing :as routing]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [compojure.route :as cpj.route]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.ring :refer [wrap-handler-with-logging]]
    [json-roa.ring-middleware.request]
    [json-roa.ring-middleware.response]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    ))


;### sanitize request params #################################################

(defn sanitize-request-params [request]
  (assoc request
         :query-params (-> request :query-params http/sanitize-query-params)
         ))

(defn wrap-sanitize-request-params [handler]
  (fn [request]
    (handler (sanitize-request-params request))))


;### include storage_servic_prefix ############################################

(defn wrap-includ-storage-service-prefix [handler]
  (fn [request]
    (handler (assoc-in request [:storage_service_prefix]
                       (let [storage-config (-> (get-config) :services :storage :http)]
                         (str (:context storage-config) (:sub_context storage-config)))))))

;### init #####################################################################


(def routes
  (cpj/routes
    (cpj/GET "/" request (resources.root/get request))

    (cpj/ANY "/jobs/" [] jobs/routes)
    (cpj/ANY "/jobs/:id/tasks/" [] tasks/routes)
    (cpj/ANY "/jobs/:id" [] job/routes)
    (cpj/ANY "/jobs/:id/*" [] job/routes)

    (cpj/ANY "/tasks/:id" [] task/routes)

    (cpj/ANY "/tasks/:id/trials/" [] trials/routes)
    (cpj/ANY "/trial/*" [] trial/routes)

    (cpj/ANY "/trial/:trial_id/trial-attachments/" [] trial-attachments/routes)
    (cpj/ANY "/trial-attachments/*" [] trial-attachment/routes)

    (cpj/ANY "/jobs/:id/tree-attachments/*" _ tree-attachments/routes)
    (cpj/ANY "/tree-attachments/*" [] tree-attachment/routes)

    (cpj/ANY "*" request {:status 404 :body {:message "404 NOT FOUND"}})
    ))

(defn build-routes-handler []
  (catcher/wrap-with-log-warn
    (-> routes
        (wrap-handler-with-logging 'cider-ci.api.resources)
        (json-roa.ring-middleware.request/wrap json-roa/handler)
        (wrap-handler-with-logging 'cider-ci.api.resources)
        json-roa.ring-middleware.response/wrap
        (wrap-handler-with-logging 'cider-ci.api.resources)
        wrap-includ-storage-service-prefix
        (wrap-handler-with-logging 'cider-ci.api.resources)
        ring.middleware.json/wrap-json-response
        wrap-sanitize-request-params
        (wrap-handler-with-logging 'cider-ci.api.resources)
        )))




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.java.jdbc)
;(debug/debug-ns 'ring.middleware.json)
;(debug/debug-ns *ns*)
