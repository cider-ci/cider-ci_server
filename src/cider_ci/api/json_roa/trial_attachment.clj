; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.trial-attachment
  (:require
    [cider-ci.api.json-roa.links :as links]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn data-stream-link [request response]
  (let [context (:context request)
        trial-id (-> response :body :trial_id)
        path (-> response :body :path)
        storage-service-prefix (-> request :storage_service_prefix)]
    (logging/info 'data-stream-link {:trial-id trial-id})
    {:name "Trial-Attachment Data"
     :href (str storage-service-prefix "/trial-attachments/" trial-id "/" path)
     :relations
     {:api-doc
      {:name "Trial-Attachment Storage Resources Documentation"
       :href (str (links/storage-api-docs-path) "#trial-attachments")}}
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
     {:trial-attachment-data-stream (data-stream-link request response)
      :trial-attachments (links/trial-attachments context trial-id)
      :trial (links/trial context trial-id)
      }}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

