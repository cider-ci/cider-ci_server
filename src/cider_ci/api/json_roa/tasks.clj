; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.tasks
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-params request)
        execution-id (-> request :params :id)
        ]
    (logging/debug build {:context context :execution-id execution-id :query-prarams query-params})
    (let [ids (-> response :body :task_ids)]
      {:relations
       {
        :self (links/tasks context execution-id query-params)
        :execution (links/execution context execution-id)
        :root (links/root context)
        }
       :collection
       (conj
         {:relations (into {} (map (fn [id]
                                     [id (links/task context id)])
                                   ids))}
         (when (seq ids)
           (links/next-link 
             (links/tasks-path context execution-id) 
             query-params)))})))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

