; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.executions
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.api.pagination :as pagination]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-params request)]
    (let [ids (->> response :body :executions (map :id))]
      (logging/debug {:ids ids})
      {:name "Executions"
       :self-relation (links/executions context query-params)
       :relations
       {:root (links/root context)
        }
       :collection
       (conj
         {:relations 
          (into {} (map-indexed 
                     (fn [i id]
                       [(+ 1 i (pagination/compute-offset query-params))
                        (links/execution context id)])
                     ids))}
         (when (seq ids)
           (links/next-rel
             (fn [query-params]
               (links/executions-path context query-params))
             query-params)))})))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

