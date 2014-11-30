; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.tree-attachment
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )



(defn data-stream-link [request response]
  (let [context (:context request)
        path (-> response :body :path)
        storage-service-prefix (-> request :storage_service_prefix)
        ]
    {:name "Tree-Attachment Data"
     :href (str storage-service-prefix "/tree-attachments" path)
     :methods {:get {}
               :delete{} 
               :put{} }
     :relations {:api-doc 
                 {:name "Tree-Attachment Storage Resources Documentation"
                  :href (str (links/api-docs-path context) "#tree-attachment-1")}}
     }))



(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        attachment-id (-> request :route-params :attachment_id)
        tree-id (->> response :body :path
                     (re-find #"^\/(\w+)\/") 
                     second)]
    {:name "Tree-Attachment"
     :self-relation (links/tree-attachment context attachment-id)
     :relations
     {:data-stream (data-stream-link request response)
      :executions (links/executions context {:tree-id tree-id})
      }}))




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
