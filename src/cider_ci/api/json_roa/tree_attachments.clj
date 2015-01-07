; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.tree-attachments
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.api.pagination :as pagination]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]))


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        execution-id (-> request :route-params :id)
        ids (->> response :body :tree_attachments (map :id))
        tree-id (:tree_id 
                  (first (jdbc/query 
                           (rdbms/get-ds) 
                           ["SELECT tree_id FROM executions WHERE id = ?" 
                            execution-id])))]
    {:name "Tree-Attachments"
     :self-relation (links/tree-attachments context execution-id)
     :relations
     {:execution (links/execution context execution-id)
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
           (links/tree-attachments-path context execution-id) 
           query-params)))}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

