; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.trial-attachment
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )



(defn data-stream-link [request response]
  (let [context (:context request)
        path (-> response :body :path)
        storage-service-prefix (-> request :storage_service_prefix)]
    {:name "Trial-Attachment Data"
     :href (str storage-service-prefix "/trial-attachments" path)
     :relations
     {:api-doc 
      {:name "Trial-Attachment Storage Resources Documentation"
       :href (str (links/api-docs-path context) "#trial-attachment-1")}}
     }))

(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        attachment-id (-> request :route-params :attachment_id)
        trial-id (->> response :body  :path 
                      (re-find #"^\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})") 
                      second)]
    {:name "Trial-Attachment" 
     :self-relation (links/trial-attachment context attachment-id)
     :relations
     {:data-stream (data-stream-link request response)
      :trial-attachments (links/trial-attachments context trial-id)
      :trial (links/trial context trial-id)
      }}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

