; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.json-roa.trial-attachments
  (:require
    [cider-ci.server.api.json-roa.links :as links]
    [cider-ci.server.api.pagination :as pagination]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        trial-id (-> request :route-params :trial_id)
        ids (->> response :body :trial_attachments (map :id))]
    {:name "Trial-Attachments"
     :self-relation (links/trial-attachments context trial-id)
     :relations
     {
      :trial (links/trial context trial-id)
      }
     :collection
     (conj
       {:relations (into {}
                         (map-indexed
                           (fn [i id]
                             [(+ 1 i (pagination/compute-offset query-params))
                              (links/trial-attachment context id) ])
                           ids))}
       (when (seq ids)
         (links/next-link
           (links/trial-attachments-path context trial-id)
           query-params)))}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

