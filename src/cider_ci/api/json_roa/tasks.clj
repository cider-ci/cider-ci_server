; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.tasks
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.api.pagination :as pagination]
    [drtom.logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-params request)
        job-id (-> request :params :id)
        ]
    (logging/debug build {:context context :job-id job-id :query-prarams query-params})
    (let [ids (->> response :body :tasks (map :id))]
      {:name "Tasks"
       :self-relation (links/tasks context job-id query-params)
       :relations
       {:job (links/job context job-id)
        }
       :collection
       (conj
         {:relations 
          (into {} 
                (map-indexed 
                  (fn [i id]
                    [(+ 1 i (pagination/compute-offset query-params))
                     (links/task context id)])
                  ids))}
         (when (seq ids)
           (links/next-rel
             #(links/tasks-path context job-id %)
             query-params)))})))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

