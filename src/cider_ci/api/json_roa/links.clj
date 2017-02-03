; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.links
  (:require
    [cider-ci.api.pagination :as pagination]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [ring.util.codec :refer [form-encode]]
    ))

;##############################################################################
;### general ##################################################################
;##############################################################################

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
                     (http-client/generate-query-string
                       (pagination/next-page-query-query-params
                         query-params)))}})

(defn next-rel [link-builder query-params]
  {:next {:href
          (link-builder (pagination/next-page-query-query-params
                          query-params))}})


;##############################################################################
;### resources per se #########################################################
;##############################################################################

;### commits ##############################################################

(defn commits-path
  ([prefix]
   (commits-path prefix {}))
  ([prefix query-params]
   (str prefix "/commits/"
        (if (empty? query-params)
          "{?repository_url,branch_head,branch_descendants,tree_id}"
          (str "?" (http-client/generate-query-string query-params))
          ))))

(defn commits
  ([prefix ]
   (commits prefix {}))
  ([prefix query-params]
   {:name "Commits"
    :href (commits-path prefix query-params)
    :relations
    {:api-doc
     {:name "API Documentation Commits"
      :href (str (api-docs-path) "#commits")}}}))

(defn commit
  ([prefix]
   (commit prefix "{id}"))
  ([prefix id]
   {:name "Commit"
    :href (str prefix "/commits/" id)
    :relations
    {:api-doc
     {:name "API Documentation Commit"
      :href (str (api-docs-path) "#commit")}
     }}))



;### jobs #################################################################

(defn jobs-path
  ([prefix]
   (jobs-path prefix {}))
  ([prefix query-params]
   (str prefix "/jobs/"
        (if (empty? query-params)
          "{?repository_url,branch_head,branch_descendants,key,state,tree_id,job_specification_id}"
          (str "?" (http-client/generate-query-string query-params)
               "{?repository_url,branch_head,branch_descendants,key,state,tree_id,job_specification_id}"
               )))))

(defn jobs
  ([prefix ]
   (jobs prefix {}))
  ([prefix query-params]
   {:name "Jobs"
    :href (jobs-path prefix query-params)
    :methods {:get {} }
    :relations
    {:api-doc
     {:name "API Documentation Jobs"
      :href (str (api-docs-path) "#jobs")}}}))

(defn create-job [prefix]
  {:name "Create Job"
   :href (str prefix "/jobs/create")
   :methods {:post {} }
   :relations
   {:api-doc
    {:name "API Documentation Create-Job"
     :href (str (api-docs-path) "#create-job")}}})

(defn job
  ([prefix]
   (job prefix "{id}"))
  ([prefix id]
   {:name "Job"
    :href (str prefix "/jobs/" id)
    :relations
    {:api-doc
     {:name "API Documentation Job"
      :href (str (api-docs-path) "#job")}
     }}))

(defn job-specification
  ([prefix]
   (job prefix "{id}"))
  ([prefix id]
   {:name "Job-Specification"
    :href (str prefix "/job-specifications/" id)
    :relations {}
    }))


;### projects #############################################################

(defn projects
  ([]
   {:name "Projects"
    :href "/cider-ci/repositories/projects/"
    :relations
    {:api-doc
     {:name "API Documentation Projects"
      :href (str (api-docs-path) "#projects")}}}))


;### script ####################################################################

(defn script-path [prefix id]
  (str prefix "/scripts/" id ))

(defn script
  ([prefix]
   (script prefix "{id}"))
  ([prefix id]
   {:name "script"
    :href (script-path prefix id)
    :relations
    {:api-doc
     {:name "API Documentation script"
      :href (str (api-docs-path) "#script")}}}))

(defn scripts-path
  ([prefix trial-id]
   (scripts-path prefix trial-id {}))
  ([prefix trial-id query-params]
   (str prefix "/trials/"  trial-id "/scripts/")))

(defn scripts
  ([prefix trial-id ]
   (scripts  prefix trial-id {}))
  ([prefix trial-id query-params]
   {:name "scripts"
    :href (scripts-path prefix trial-id query-params)
    :relations
    {:api-doc
     {:name "API Documentation scripts"
      :href (str (api-docs-path) "#scripts")}}}))



;### tasks #####################################################################

(defn task-path [prefix id]
  (str prefix "/tasks/" id ))

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

(defn task-specification
  ([prefix]
   (task-specification prefix "{id}"))
  ([prefix id]
   {:name "task-Specification"
    :href (str prefix "/task-specifications/" id)
    :relations {}
    }))

(defn tasks-path [prefix query-params]
  (str prefix "/tasks/"
       (if (empty? query-params)
         "{?job_id,state,task_specification_id}"
         (str "?" (http-client/generate-query-string query-params)
              "{?job_id,state,task_specification_id}"))))

(defn tasks
  ([prefix]
   (tasks prefix {}))
  ([prefix query-params]
   {:name "Tasks"
    :href (tasks-path prefix query-params)
    :relations
    {:api-doc
     {:name "API Documentation Task"
      :href (str (api-docs-path) "#tasks")}}}))


;### trials ##################################################################

(defn trial-path [prefix id]
  (str prefix "/trials/" id ))

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

(defn trials-path
  ([prefix task-id]
   (trials-path prefix task-id {}))
  ([prefix task-id query-params]
   (str prefix "/tasks/"  task-id "/trials/"
        (if (empty? query-params)
          "{?state}"
          (str "?" (http-client/generate-query-string query-params))))))

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

(defn retry [prefix task-id]
  {:name "Retry Task"
   :href (str prefix (str "/tasks/" task-id "/trials/retry"))
   :methods {:post {} }
   :relations
   {:api-doc
    {:name "API Documentation Retry"
     :href (str (api-docs-path) "#retry")}}})


;### trial attachments ######################################################

(defn trial-attachment-path
  ([prefix attachment-id ]
   (str prefix "/trial-attachments/"  attachment-id)))

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
   (str prefix "/trials/"  trial-id "/trial-attachments/"
        (if (empty? query-params)
          "{?pathsegment}"
          (str "?" (http-client/generate-query-string query-params))))))

(defn trial-attachments
  ([prefix trial-id]
   (trial-attachments prefix trial-id {}))
  ([prefix trial-id query-params]
   {:name "Trial-Attachments"
    :href (trial-attachments-path prefix trial-id query-params)
    }))


;### tree attachments #########################################################

(defn tree-attachment-path
  ([prefix attachment-id]
   (str prefix "/tree-attachments/"  attachment-id)))

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

(defn tree-attachments-path
  ([prefix job-id]
   (tree-attachments-path prefix job-id {}))
  ([prefix job-id query-params]
   (str prefix "/jobs/"  job-id "/tree-attachments/"
        (when-not  (empty? query-params)
          (str "?" (http-client/generate-query-string query-params))))))

(defn tree-attachments
  ([prefix job-id]
   (tree-attachments prefix job-id {}))
  ([prefix job-id query-params]
   {:name "Tree-Attachments"
    :href (tree-attachments-path prefix job-id query-params)
    }))

(defn- tree-attachment-data-stream-val [context]
  {:name "Tree-Attachment Data"
   ; :href (str storage-service-prefix "/tree-attachments" path)
   :methods {:get {}
             :delete {}
             :put {} }
   :relations {:api-doc
               {:name "Tree-Attachment Storage Resources Documentation"
                :href (str (storage-api-docs-path) "#tree-attachments")}}})

(defn tree-attachment-data-stream
  ([request tree-id path]
   (let [context (:context request)
         storage-service-prefix (-> request :storage_service_prefix) ]
     (assoc-in (tree-attachment-data-stream-val context)
               [:href]
               (str storage-service-prefix
                    "/tree-attachments/" tree-id "/" path)))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
