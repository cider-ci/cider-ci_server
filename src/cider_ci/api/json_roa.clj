; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.json-roa
  (:require 
    [cider-ci.api.json-roa.job :as json-roa.job]
    [cider-ci.api.json-roa.jobs :as json-roa.jobs]
    [cider-ci.api.json-roa.links :as json-roa.links]
    [cider-ci.api.json-roa.root :as json-roa.root]
    [cider-ci.api.json-roa.task :as json-roa.task]
    [cider-ci.api.json-roa.tasks :as json-roa.tasks]
    [cider-ci.api.json-roa.tree-attachment :as json-roa.tree-attachment]
    [cider-ci.api.json-roa.tree-attachments :as json-roa.tree-attachments]
    [cider-ci.api.json-roa.trial :as json-roa.trial]
    [cider-ci.api.json-roa.trial-attachment :as json-roa.trial-attachment]
    [cider-ci.api.json-roa.trial-attachments :as json-roa.trial-attachments]
    [cider-ci.api.json-roa.trials :as json-roa.trials]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.routing :as routing]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [ring.middleware.accept]
    [ring.util.response :as response]
    ))



;### Amend response ###########################################################

(def about 
  {:name "JSON-ROA"
   :description "A JSON extension for resource and relation oriented architectures providing explorable APIs for humans and machines."
   :relations{:json-roa_homepage
              {:ref "http://json-roa.github.io/"
               :name "JSON-ROA Homepage"}}})

 
(defn amend-json-roa [request json-roa-data]
  (-> {}
      (assoc-in [:_json-roa] json-roa-data)
      (assoc-in [:_json-roa :about_json-roa] about)
      (assoc-in [:_json-roa :version] "1.0.0-beta.0+2014, 2015 1130")
      ))

;### Routing ##################################################################

(defn build-routes-handler [json-response]
  (cpj/routes
    (cpj/GET "/" request (json-roa.root/build request))
    (cpj/GET "/jobs/" request (json-roa.jobs/build request json-response))
    (cpj/GET "/job/:id" request (json-roa.job/build request json-response))
    (cpj/GET "/job/:id/tasks/" request (json-roa.tasks/build request json-response))
    (cpj/GET "/task/:id" request (json-roa.task/build request json-response))
    (cpj/GET "/task/:id/trials/" request (json-roa.trials/build request json-response))
    (cpj/GET "/trial/:id" request (json-roa.trial/build request json-response))

    (cpj/ANY "/trial/:trial_id/trial-attachments/" request (json-roa.trial-attachments/build request json-response))
    (cpj/ANY "/trial-attachment/:id" request (json-roa.trial-attachment/build request json-response))

    (cpj/ANY "/job/:id/tree-attachments/" request (json-roa.tree-attachments/build request json-response))
    (cpj/ANY "/tree-attachment/:id" request (json-roa.tree-attachment/build request json-response))


    ))

(defn builder [request json-response]
  (let [json-roa-handler (build-routes-handler json-response)
        json-roa-data (select-keys (json-roa-handler request) [:self-relation :relations :collection :name]) 
        amended-json-roa-data  (amend-json-roa request json-roa-data)]
    (logging/debug builder {:json-response json-response :json-roa-data json-roa-data})
    (update-in json-response 
               [:body] 
               (fn [original-body json-road-data] 
                 (into {} (sort (conj {} original-body json-road-data)))) 
               amended-json-roa-data )))


;### wrap negotiate accept ####################################################

(defn wrap-negotiate-accept [handler]
  (ring.middleware.accept/wrap-accept 
    handler
    {:mime 
     ["application/json-roa+json" :qs 1 
      "application/json" :qs 0.5
      ]}))

;### dispatch #################################################################

(defn dispatch [request json-response]
  (let [mime (or (-> request :accept :mime) 
                 "application/json-roa+json")]
    (if (re-matches #"application\/.*\bjson-roa\b.*" mime)
      (builder request json-response)
      json-response)))


(defn wrap-dispatch [handler]
  (fn [request]
    (let [response (handler request)]
      (dispatch request response)
      )))

;### accept ###################################################################

(defn not-acceptable [request]
  (-> 
    {:status 406
     :body "This resource accepts 'application/json-roa+json' or 'application/json' only."}
    (response/header "Content-Type" "text/plain")
    (response/charset "UTF8")))

(defn wrap-accept [handler]
  (fn [request]
    (let [mime (-> request :accept :mime)] 
      (cond 
        (not mime) (not-acceptable request)
        (re-matches #".*json.*" mime) (handler request)
        :else (not-acceptable request)))))

;### wrap #####################################################################

(defn wrap [handler]
  (-> handler 
      (routing/wrap-debug-logging 'cider-ci.api.json-roa)
      wrap-dispatch
      (routing/wrap-debug-logging 'cider-ci.api.json-roa)
      wrap-accept
      (routing/wrap-debug-logging 'cider-ci.api.json-roa)
      wrap-negotiate-accept
      (routing/wrap-debug-logging 'cider-ci.api.json-roa)
      )) 
  

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.java.jdbc)
;(debug/debug-ns *ns*)
