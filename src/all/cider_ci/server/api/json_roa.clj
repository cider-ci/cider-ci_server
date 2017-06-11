; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.server.api.json-roa
  (:require
    [cider-ci.server.api.json-roa.git :as json-roa.git]
    [cider-ci.server.api.json-roa.job :as json-roa.job]
    [cider-ci.server.api.json-roa.job-specification :as json-roa.job-specification]
    [cider-ci.server.api.json-roa.jobs :as json-roa.jobs]
    [cider-ci.server.api.json-roa.links :as json-roa.links]
    [cider-ci.server.api.json-roa.root :as json-roa.root]
    [cider-ci.server.api.json-roa.script :as json-roa.script]
    [cider-ci.server.api.json-roa.task :as json-roa.task]
    [cider-ci.server.api.json-roa.task-specification :as json-roa.task-specification]
    [cider-ci.server.api.json-roa.tasks :as json-roa.tasks]
    [cider-ci.server.api.json-roa.tree-attachment :as json-roa.tree-attachment]
    [cider-ci.server.api.json-roa.tree-attachments :as json-roa.tree-attachments]
    [cider-ci.server.api.json-roa.trial :as json-roa.trial]
    [cider-ci.server.api.json-roa.trial-attachment :as json-roa.trial-attachment]
    [cider-ci.server.api.json-roa.trial-attachments :as json-roa.trial-attachments]
    [cider-ci.server.api.json-roa.trials :as json-roa.trials]
    [cider-ci.server.api.json-roa.scripts :as json-roa.scripts]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.routing :as routing]
    [compojure.core :as cpj]
    [ring.middleware.accept]
    [ring.util.response :as response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))



;### Amend response ###########################################################

(def about
  {:name "JSON-ROA"
   :description "A JSON extension for resource and relation oriented architectures providing explorable APIs for humans and machines."
   :relations{:json-roa_homepage
              {:ref "http://json-roa.github.io/"
               :name "JSON-ROA Homepage"}}})

(defn- add-root-link [request json-roa-data]
  (let [relations (or (-> json-roa-data :relations) {})
        relations-with-root (assoc relations :root (json-roa.links/root (:context request)))]
    (logging/debug {:relations-with-root relations-with-root})
    (assoc json-roa-data :relations relations-with-root)))

(defn amend-json-roa [request json-roa-data]
  (-> {}
      (assoc-in [:_json-roa] (add-root-link request json-roa-data))
      (assoc-in [:_json-roa :about_json-roa] about)
      (assoc-in [:_json-roa :json-roa_version] "1.0.0")))

;### Routing ##################################################################

(defn build-routes-handler [json-response]
  (cpj/routes

    (cpj/GET "/" request (json-roa.root/build request))
    (cpj/GET "/jobs/" request (json-roa.jobs/build request json-response))
    (cpj/GET "/jobs/:id" request (json-roa.job/build request json-response))
    (cpj/POST "/jobs/create" request (json-roa.job/build request json-response))
    (cpj/GET "/tasks/" request (json-roa.tasks/build request json-response))

    (cpj/GET "/commits*" request (json-roa.git/build request json-response))

    (cpj/GET "/tasks/:id" request (json-roa.task/build request json-response))
    (cpj/GET "/tasks/:id/trials/" request (json-roa.trials/build request json-response))


    (cpj/GET "/job-specifications/:id" request (json-roa.job-specification/build request json-response))
    (cpj/GET "/task-specifications/:id" request (json-roa.task-specification/build request json-response))

    (cpj/GET "/scripts/:id" request (json-roa.script/build request json-response))
    (cpj/GET "/trials/:id/scripts/" request (json-roa.scripts/build request json-response))

    (cpj/GET "/trials/:id" request (json-roa.trial/build request json-response))

    (cpj/POST "/tasks/:id/trials/retry" request (json-roa.trial/build request json-response))

    (cpj/ANY "/trials/:trial_id/trial-attachments/" request (json-roa.trial-attachments/build request json-response))
    (cpj/ANY "/trial-attachments/:id" request (json-roa.trial-attachment/build request json-response))

    (cpj/ANY "/jobs/:id/tree-attachments/" request (json-roa.tree-attachments/build request json-response))
    (cpj/ANY "/tree-attachments/:id" request (json-roa.tree-attachment/build request json-response))

    ))

(defn handler [request json-response]
  (let [json-roa-handler (build-routes-handler json-response)
        json-roa-data (select-keys (json-roa-handler request) [:self-relation :relations :collection :name])
        amended-json-roa-data  (amend-json-roa request json-roa-data)]
    (logging/debug 'handler {:json-response json-response :json-roa-data json-roa-data})
    (update-in json-response
               [:body]
               (fn [original-body json-road-data]
                 (into {} (sort (conj {} original-body json-road-data))))
               amended-json-roa-data )))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.java.jdbc)
;(debug/debug-ns *ns*)
