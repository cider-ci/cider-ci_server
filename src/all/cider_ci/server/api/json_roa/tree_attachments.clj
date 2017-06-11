; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.json-roa.tree-attachments
  (:require
    [cider-ci.server.api.json-roa.links :as links]
    [cider-ci.server.api.pagination :as pagination]
    [logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]))


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        job-id (-> request :route-params :id)
        ids (->> response :body :tree_attachments (map :id))
        tree-id (:tree_id
                  (first (jdbc/query
                           (rdbms/get-ds)
                           ["SELECT tree_id FROM jobs WHERE id = ?"
                            job-id])))]
    {:name "Tree-Attachments"
     :self-relation (links/tree-attachments context job-id)
     :relations
     {:job (links/job context job-id)
      :tree-attachment-data-stream (links/tree-attachment-data-stream request tree-id "{path}")
      }
     :collection
     (conj
       {:relations
        (into {}
              (map-indexed
                (fn [i id]
                  [(+ 1 i (pagination/compute-offset query-params))
                   (links/tree-attachment context id)
                   ])
                ids))}
       (when (seq ids)
         (links/next-link
           (links/tree-attachments-path context job-id)
           query-params)))}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

