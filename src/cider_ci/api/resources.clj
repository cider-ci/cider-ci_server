; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.resources
  (:require 
    [cider-ci.api.json-roa :as json-roa]
    [cider-ci.api.resources.execution :as execution]
    [cider-ci.api.resources.executions :as executions]
    [cider-ci.api.resources.root :as resources.root]
    [cider-ci.api.resources.task :as task]
    [cider-ci.api.resources.tasks :as tasks]
    [cider-ci.api.resources.trials :as trials]
    [cider-ci.api.resources.trial :as trial]
    [cider-ci.api.resources.trial-attachments :as trial-attachments]
    [cider-ci.api.resources.trial-attachment :as trial-attachment]
    [cider-ci.api.resources.tree-attachments :as tree-attachments]
    [cider-ci.api.resources.tree-attachment :as tree-attachment]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.with :as with]
    [json-roa.ring-middleware]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [compojure.route :as cpj.route]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    ))


(defonce conf (atom nil))

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
                       (http/build-url (:storage_service @conf) "")))))



;### init #####################################################################


(def routes 
  (cpj/routes 
    (cpj/GET "/" request (resources.root/get request))

    (cpj/ANY "/executions/" [] executions/routes)
    (cpj/ANY "/execution/*" [] execution/routes)

    (cpj/ANY "/execution/:id/tasks/" [] tasks/routes)
    (cpj/ANY "/task/*" [] task/routes)

    (cpj/ANY "/task/:id/trials/" [] trials/routes)
    (cpj/ANY "/trial/*" [] trial/routes)

    (cpj/ANY "/trial/:trial_id/trial-attachments/" [] trial-attachments/routes)
    (cpj/ANY "/trial-attachment/*" [] trial-attachment/routes)

    (cpj/ANY "/execution/:id/tree-attachments/" [] tree-attachments/routes)
    (cpj/ANY "/tree-attachment/*" [] tree-attachment/routes)

    (cpj/ANY "*" request {:status 404 :body {:message "404 NOT FOUND"}})
    ))

(defn build-routes-handler []
  (with/logging
    (-> routes 
        (routing/wrap-debug-logging 'cider-ci.api.resources)
        json-roa/wrap
        (routing/wrap-debug-logging 'cider-ci.api.resources)
        json-roa.ring-middleware/wrap-roa-json-response
        (routing/wrap-debug-logging 'cider-ci.api.resources)
        wrap-includ-storage-service-prefix
        (routing/wrap-debug-logging 'cider-ci.api.resources)
        ring.middleware.json/wrap-json-response
        wrap-sanitize-request-params
        (routing/wrap-debug-logging 'cider-ci.api.resources)
        )))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.java.jdbc)
;(debug/debug-ns 'ring.middleware.json)
;(debug/debug-ns *ns*)
