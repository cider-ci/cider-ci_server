(ns cider-ci.api.resources.shared
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    [sqlingvo.core :as sqling]
    ) 
  (:refer-clojure :exclude [distinct group-by])
  (:use 
    [sqlingvo.core]
    ))


;### config ###################################################################

(defonce ^:private conf (atom nil))

;### helper ###################################################################
(defn uuid [str-or-uuid]
  (if (= (type str-or-uuid) java.util.UUID)
    uuid
    (java.util.UUID/fromString str-or-uuid)))

;### url ######################################################################
(defn prefix []
  (str (-> @conf :web :context) "/api_v1"))

(defn curies-link-map []
  {:curies [{:name "cider-ci_api-docs"
            :href (str (prefix) "/doc/api/index.html#{rel}")
            :templated true}]})

;### offset ###################################################################
(defn page-number [params]
  (let [page-string (:page params)]
    (if page-string (Integer/parseInt page-string)
      0)))

(defn compute-offset [params]
  (let [page (page-number params)]
    (* 10 page)))

(defn add-offset [query params]
  (let [off (compute-offset params)]
    (compose query (offset off) (limit 10))))

(defn next-page-query-params [params]
  (let [i-page (page-number params)]
    (assoc params 
           :page (+ i-page 1))))

(defn previous-page-query-params [params]
  (let [i-page (page-number params)]
    (when (> i-page 0)
      (assoc params 
             :page (- i-page 1)))))

(defn next-and-previous-link-map  [url-path params next?]
  (conj {}
        (when-let [pp (previous-page-query-params params)]
          {:previous {:href (str url-path "?" (http/build-url-query-string pp))}})
        (when next?
          {:next {:href (str url-path "?" 
                             (http/build-url-query-string 
                               (next-page-query-params params)))}})))

;##############################################################################
(defn execution-path [id]
  (str (prefix) "/execution/" id))

(defn execution-link [id]
  {:href (execution-path id)
   :title "Execution"
   })

(defn execution-link-map [id]
  {:cider-ci_api-docs:execution
   (execution-link id)})

;##############################################################################
(defn execution-stats-link-map [id]
  {:cider-ci_api-docs:execution-stats
   {:href (str (execution-path id) "/stats")
    :title "Execution-Stats"}})
    
;##############################################################################
(defn executions-path []
  (str (prefix) "/executions"))

(defn executions-link []
  {:href (executions-path)
   :title "Executions"} )

(defn executions-link-map []
  {:cider-ci_api-docs:executions
   (executions-link) })

;##############################################################################
(defn tasks-path [execution-id]
  (str (prefix) "/execution/" execution-id "/tasks"))

(defn tasks-link-map [execution-id]
  {:cider-ci_api-docs:tasks
    {:title "Tasks" 
     :href (tasks-path execution-id)}})

(defn task-link [task-id]
  {:href (str (prefix) "/task/" task-id) 
   :title "Task"})

(defn task-link-map [task-id]
  {:cider-ci_api-docs:task
   (task-link task-id)})

;##############################################################################
(defn trials-path [task-id]
  (str (prefix) "/task/" task-id "/trials"))

(defn trials-link-map [tasks-id]
  {:cider-ci_api-docs:trials
    {:title "Trials" 
     :href (trials-path tasks-id)}})

(defn trial-link [trial-id]
  {:href (str (prefix) "/trial/" trial-id) 
   :title "Trial"})

(defn trial-link-map [trial-id]
  {:cider-ci_api-docs:trial 
    (trial-link trial-id)})

;##############################################################################
(defn trial-attachments-path [trial-id]
  (str (prefix) "/trial/" trial-id "/attachments"))

(defn trial-attachments-link [trial-id]
  {:title "Trial-Attachments" 
   :href (trial-attachments-path trial-id)})

(defn trial-attachments-link-map [trial-id]
  {:cider-ci_api-docs:trial-attachments
   (trial-attachments-link trial-id)})

;##############################################################################
(defn trial-attachment-path [path]
  (str (prefix) "/trial-attachment" path))

(defn trial-attachment-link [path]
  {:title "Trial-Attachment" 
   :href (trial-attachment-path path)})

(defn trial-attachment-link-map [path]
  {:cider-ci_api-docs:trial-attachment
   (trial-attachment-link path)}) 

;##############################################################################
(defn root-link []
  {:href  (prefix) :title "API-Root"}) 

(defn root-link-map []
  {:cider-ci_api-docs:root (root-link)})


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


