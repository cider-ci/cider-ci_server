; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.links
  (:require
    [cider-ci.api.pagination :as pagination]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [ring.util.codec :refer [form-encode]]
    ))


(defn api-docs-path []
  (str "/cider-ci/docs/api/api_resources.html"))

(defn storage-api-docs-path []
  "/cider-ci/docs/api/storage_resources.html")

;### root #######################################################################

(defn root
  ([prefix]
   {:name "Root"
    :href (str prefix "/")
    :relations 
    {:api-doc
     {:name "API Documentation for Cider-CI"
      :href (str (api-docs-path) "#cider-ci-api-documentation") 
      }}}))


;### next link #################################################################

(defn next-link [url-path query-params]
  {:next {:href (str url-path "?" 
                     (http/build-url-query-string 
                       (pagination/next-page-query-query-params 
                         query-params)))}})

(defn next-rel [link-builder query-params]
  {:next {:href
          (link-builder (pagination/next-page-query-query-params 
                          query-params))}})


;### jobs #################################################################

(defn jobs-path 
  ([prefix]
   (jobs-path prefix {}))
  ([prefix query-params]
   (str prefix "/jobs/" 
        (if (empty? query-params)
          "{?repository,branch,branchdescendants,state,treeid}"
          (str "?" (http/build-url-query-string query-params))
          ))))

(defn jobs
  ([prefix ]
   (jobs prefix {}))
  ([prefix query-params]
   {:name "Jobs"
    :href (jobs-path prefix query-params)
    :relations
    {:api-doc 
     {:name "API Documentation Jobs" 
      :href (str (api-docs-path) "#jobs")}}}))


;### job ##################################################################

(defn job
  ([prefix]
   (job prefix "{id}"))
  ([prefix id]
   {:name "Job"
    :href (str prefix "/job/" id)
    :relations
    {:api-doc 
     {:name "API Documentation Job" 
      :href (str (api-docs-path) "#job")}
     }}))


;### task #######################################################################

(defn task-path [prefix id]
  (str prefix "/task/" id ))

(defn task
  ([prefix]
   (task prefix "{id}"))
  ([prefix id]
   {:name "Task"
    :href (task-path prefix id)
    :relations
    {:api-doc 
     {:name "API Documentation Task" 
      :href (str (api-docs-path) "#task")}}}))


;### tasks #################################################################

(defn tasks-path 
  ([prefix job-id]
   (tasks-path prefix job-id {}))
  ([prefix job-id query-params]
   (str prefix "/job/"  job-id "/tasks/"
        (if (empty? query-params)
          "{?state}"
          (str "?" (http/build-url-query-string query-params))))))

(defn tasks
  ([prefix job-id ]
   (tasks  prefix job-id {}))
  ([prefix job-id query-params]
   {:name "Tasks"
    :href (tasks-path prefix job-id query-params)
    :relations
    {:api-doc 
     {:name "API Documentation Task" 
      :href (str (api-docs-path) "#tasks")}}}))


;### trial #######################################################################

(defn trial-path [prefix id]
  (str prefix "/trial/" id ))

(defn trial
  ([prefix]
   (trial prefix "{id}"))
  ([prefix id]
   {:name "Trial"
    :href (trial-path prefix id)
    :relations
    {:api-doc 
     {:name "API Documentation Trial" 
      :href (str (api-docs-path) "#trial")}}}))


;### trials #################################################################

(defn trials-path 
  ([prefix task-id]
   (trials-path prefix task-id {}))
  ([prefix task-id query-params]
   (str prefix "/task/"  task-id "/trials/"
        (if (empty? query-params)
          "{?state}"
          (str "?" (http/build-url-query-string query-params))))))

(defn trials
  ([prefix task-id ]
   (trials  prefix task-id {}))
  ([prefix task-id query-params]
   {:name "Trials"
    :href (trials-path prefix task-id query-params)
    :relations
    {:api-doc 
     {:name "API Documentation Trials"
      :href (str (api-docs-path) "#trials")}}}))


;### trial attachments ######################################################

(defn trial-attachment-path
  ([prefix attachment-id ]
   (str prefix "/trial-attachment/"  attachment-id)))

(defn trial-attachment 
  ([prefix ]
   (trial-attachment prefix "{id}"))
  ([prefix attachment-id]
   {:name "Trial-attachment"
    :href (trial-attachment-path prefix attachment-id)
    :relations
    {:api-doc 
     {:name "Trial-Attachments API Documentation"
      :href (str (api-docs-path) "#trial-attachments")}}
    }))


;### trial attachments ######################################################

(defn trial-attachments-path
  ([prefix trial-id]
   (trial-attachments-path prefix trial-id {}))
  ([prefix trial-id query-params]
   (str prefix "/trial/"  trial-id "/trial-attachments/"
        (if (empty? query-params)
          "{?pathsegment}"
          (str "?" (http/build-url-query-string query-params))))))

(defn trial-attachments 
  ([prefix trial-id]
   (trial-attachments prefix trial-id {}))
  ([prefix trial-id query-params]
   {:name "Trial-Attachments"
    :href (trial-attachments-path prefix trial-id query-params)
    }))


;### tree attachment ######################################################

(defn tree-attachment-path
  ([prefix attachment-id]
   (str prefix "/tree-attachment/"  attachment-id)))

(defn tree-attachment 
  ([prefix ]
   (tree-attachment prefix "{attachment_id}"))
  ([prefix attachment-id]
   {:name "Tree-Attachment"
    :href (tree-attachment-path prefix attachment-id)
    :relations
    {:api-doc 
     {:name "Documentation Tree-Attachments"
      :href (str (api-docs-path) "#tree-attachments")}}
    }))


;### tree attachments ######################################################

(defn tree-attachments-path
  ([prefix job-id]
   (tree-attachments-path prefix job-id {}))
  ([prefix job-id query-params]
   (str prefix "/job/"  job-id "/tree-attachments/"
        (when-not  (empty? query-params)
          (str "?" (http/build-url-query-string query-params))))))

(defn tree-attachments 
  ([prefix job-id]
   (tree-attachments prefix job-id {}))
  ([prefix job-id query-params]
   {:name "Tree-Attachments"
    :href (tree-attachments-path prefix job-id query-params)
    }))


;### tree attachments data stream link #####################################


(defn-  tree-attachment-data-stream-val [context]
  {:name "Tree-Attachment Data"
   ; :href (str storage-service-prefix "/tree-attachments" path)
   :methods {:get {}
             :delete {} 
             :put {} }
   :relations {:api-doc 
               {:name "Tree-Attachment Storage Resources Documentation"
                :href (str (storage-api-docs-path) "#tree-attachments")}}})

(defn tree-attachment-data-stream
  ([request tree-id path-wo-slash]
   (let [context (:context request)
         storage-service-prefix (-> request :storage_service_prefix)
         ]
     (assoc-in (tree-attachment-data-stream-val context)
               [:href]
               (str storage-service-prefix 
                    "/tree-attachments/" tree-id "/" path-wo-slash)))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
