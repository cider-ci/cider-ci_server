; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.tree-attachment
  (:require
    [cider-ci.api.json-roa.links :as links]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )

(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        attachment-id (-> request :route-params :attachment_id)
        tree-id (-> response :body :tree_id)
        path (-> response :body :path)]
    {:name "Tree-Attachment"
     :self-relation (links/tree-attachment context attachment-id)
     :relations
     {:tree-attachment-data-stream (links/tree-attachment-data-stream
                                     request tree-id path)
      :jobs (links/jobs context {:tree-id tree-id})
      }}))




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
