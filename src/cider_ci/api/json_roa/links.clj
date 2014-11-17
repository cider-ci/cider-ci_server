; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.links
  (:require
    [cider-ci.api.pagination :as pagination]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))


(defn api-docs-path [context]
  (str context "/doc/api/index.html"))


;### root #######################################################################

(defn root
  ([prefix]
   {:href (str prefix "/")
    :relations 
    {:api-doc
     {:name "Cider-CI API Documentation"
      :href (str (api-docs-path prefix) "#cider-ci-api-documentation") 
      }}}))


;### next link #################################################################

(defn next-link [url-path query-params]
  {:next {:href (str url-path "?" 
                     (http/build-url-query-string 
                       (pagination/next-page-query-query-params 
                         query-params)))}})


;### executions #################################################################

(defn executions-path 
  ([prefix]
   (executions-path prefix {}))
  ([prefix query-params]
   (str prefix "/executions/" 
        (when-not (empty? query-params)
          (str "?" (http/build-url-query-string query-params))))))

(defn executions
  ([prefix ]
   (executions prefix {}))
  ([prefix query-params]
   {:name "Executions"
    :href (executions-path prefix query-params)
    :relations
    {:api-doc 
     {:name "Executions API Documentation" 
      :href (str (api-docs-path prefix) "#executions")}}}))


;### execution ##################################################################

(defn execution
  ([prefix]
   (execution prefix "{id}"))
  ([prefix id]
   {:name "Execution"
    :href (str prefix "/execution/" id)
    :relations
    {:api-doc 
     {:name "Execution API Documentation" 
      :href (str (api-docs-path prefix) "#execution")}
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
     {:name "Task API Documentation" 
      :href (str (api-docs-path prefix) "#task")}}}))


;### tasks #################################################################

(defn tasks-path 
  ([prefix execution-id]
   (tasks-path prefix execution-id {}))
  ([prefix execution-id query-params]
   (str prefix "/execution/"  execution-id "/tasks/"
        (when-not (empty? query-params)
          (str "?" (http/build-url-query-string query-params))))))

(defn tasks
  ([prefix execution-id ]
   (tasks  prefix execution-id {}))
  ([prefix execution-id query-params]
   {:name "tasks"
    :href (tasks-path prefix execution-id query-params)
    :relations
    {:api-doc 
     { :name "Task API Documentation" 
      :href (str (api-docs-path prefix) "#tasks")}}}))


;### trial #######################################################################

(defn trial-path [prefix id]
  (str prefix "/trial/" id ))

(defn trial
  ([prefix]
   (trial prefix "{id}"))
  ([prefix id]
   {:name "trial"
    :href (trial-path prefix id)
    :relations
    {:api-doc 
     {:name "Trial API Documentation" 
      :href (str (api-docs-path prefix) "#trial")}}}))


;### trials #################################################################

(defn trials-path 
  ([prefix task-id]
   (trials-path prefix task-id {}))
  ([prefix task-id query-params]
   (str prefix "/task/"  task-id "/trials/"
        (when-not (empty? query-params)
          (str "?" (http/build-url-query-string query-params))))))

(defn trials
  ([prefix task-id ]
   (trials  prefix task-id {}))
  ([prefix task-id query-params]
   {:name "Trials"
    :href (trials-path prefix task-id query-params)
    :relations
    {:api-doc 
     {:name "Trials API Documentation"
      :href (str (api-docs-path prefix) "#trials")}}}))


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
      :href (str (api-docs-path prefix) "#trial-attachments")}}
    }))


;### trial attachments ######################################################

(defn trial-attachments-path
  ([prefix trial-id]
   (trial-attachments-path prefix trial-id {}))
  ([prefix trial-id query-params]
   (str prefix "/trial/"  trial-id "/trial-attachments/"
        (when-not (empty? query-params)))))

(defn trial-attachments 
  ([prefix trial-id]
   (trial-attachments prefix trial-id {}))
  ([prefix trial-id query-params]
   {:name "Trial-Attachments"
    :href (trial-attachments-path prefix trial-id query-params)
    }))


;### tree attachments ######################################################

(defn tree-attachment-path
  ([prefix attachment-id ]
   (str prefix "/tree-attachment/"  attachment-id)))

(defn tree-attachment 
  ([prefix ]
   (tree-attachment prefix "{attachment_id}"))
  ([prefix attachment-id]
   {:name "Tree-attachment"
    :href (tree-attachment-path prefix attachment-id)
    :relations
    {:api-doc 
     {:name "Tree-Attachments API Documentation"
      :href (str (api-docs-path prefix) "#tree-attachments")}}
    }))


;### tree attachments ######################################################

(defn tree-attachments-path
  ([prefix execution-id]
   (tree-attachments-path prefix execution-id {}))
  ([prefix execution-id query-params]
   (str prefix "/execution/"  execution-id "/tree-attachments/"
        (when-not (empty? query-params)))))

(defn tree-attachments 
  ([prefix execution-id]
   (tree-attachments prefix execution-id {}))
  ([prefix execution-id query-params]
   {:name "Tree-Attachments"
    :href (tree-attachments-path prefix execution-id query-params)
    }))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
