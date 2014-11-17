; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.json-roa
  (:require 
    [cider-ci.api.json-roa.execution :as json-roa.execution]
    [cider-ci.api.json-roa.executions :as json-roa.executions]
    [cider-ci.api.json-roa.links :as json-roa.links]
    [cider-ci.api.json-roa.root :as json-roa.root]
    [cider-ci.api.json-roa.task :as json-roa.task]
    [cider-ci.api.json-roa.tasks :as json-roa.tasks]
    [cider-ci.api.json-roa.trial :as json-roa.trial]
    [cider-ci.api.json-roa.trial-attachments :as json-roa.trial-attachments]
    [cider-ci.api.json-roa.trial-attachment :as json-roa.trial-attachment]
    [cider-ci.api.json-roa.tree-attachments :as json-roa.tree-attachments]
    [cider-ci.api.json-roa.tree-attachment :as json-roa.tree-attachment]
    [cider-ci.api.json-roa.trials :as json-roa.trials]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [ring.middleware.accept]
    ))



;### Amend response ###########################################################

(def about 
  {:name "JSON-ROA"
   :description "A JSON extension for resource and relation oriented architectures providing explorable APIs for humans and machines."
   :relations{:code-repositories
              {:ref "https://github.com/json-roa/"
               :name "JSON-ROA Code Repositories"
               }}})

 
(defn amend-json-roa [request json-roa-data]
  (-> {}
      (assoc-in [:_json-roa] json-roa-data)
      (assoc-in [:_json-roa :about_json-roa] about)
      (assoc-in [:_json-roa :version] "0.1.0-rc.1")
      ))

;### Routing ##################################################################

(defn build-routes-handler [json-response]
  (cpj/routes
    (cpj/GET "/" request (json-roa.root/build request))
    (cpj/GET "/executions/" request (json-roa.executions/build request json-response))
    (cpj/GET "/execution/:id" request (json-roa.execution/build request json-response))
    (cpj/GET "/execution/:id/tasks/" request (json-roa.tasks/build request json-response))
    (cpj/GET "/task/:id" request (json-roa.task/build request json-response))
    (cpj/GET "/task/:id/trials/" request (json-roa.trials/build request json-response))
    (cpj/GET "/trial/:id" request (json-roa.trial/build request json-response))

    (cpj/ANY "/trial/:trial_id/trial-attachments/" request (json-roa.trial-attachments/build request json-response))
    (cpj/ANY "/trial-attachment/:id" request (json-roa.trial-attachment/build request json-response))

    (cpj/ANY "/execution/:id/tree-attachments/" request (json-roa.tree-attachments/build request json-response))
    (cpj/ANY "/tree-attachment/:id" request (json-roa.tree-attachment/build request json-response))


    ))

(defn builder [request json-response]
  (let [json-roa-handler (build-routes-handler json-response)
        json-roa-data (select-keys (json-roa-handler request) [:relations :collection]) 
        amended-json-roa-data  (amend-json-roa request json-roa-data)]
    (logging/debug builder {:json-response json-response :json-roa-data json-roa-data})
    (update-in json-response 
               [:body] 
               (fn [original-body json-road-data] 
                 (into {} (sort (conj {} original-body json-road-data)))) 
               amended-json-roa-data )))


;### Debug ####################################################################

(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept 
    handler
    {:mime 
     ["application/json-roa+json" :qs 1 
      "application/json" :qs 0.5
      ]}))

(defn dispatch [request json-response]
  (let [mime (or (-> request :accept :mime) 
                 "application/json-roa+json")]
    (if (re-matches #"application\/.*\bjson-roa\b.*" mime)
      (builder request json-response)
      json-response)))

(defn wrap [handler]
  (fn [request]
    (let [json-response ((wrap-accept handler) request)]
      (dispatch request json-response)
      )))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.java.jdbc)
;(debug/debug-ns *ns*)
