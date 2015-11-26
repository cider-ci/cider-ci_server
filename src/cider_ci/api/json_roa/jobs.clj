; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.jobs
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.api.pagination :as pagination]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-params request)
        ids (->> response :body :jobs (map :id))
        offset (pagination/compute-offset query-params)]
    {:name "Jobs"
     :self-relation (links/jobs context query-params)
     :relations {}
     :collection
     (conj
       {:relations
        (into {} (map-indexed
                   (fn [i id]
                     [(+ 1 i offset) (links/job context id)])
                   ids))}
       (when (seq ids)
         (links/next-rel
           (fn [query-params]
             (links/jobs-path context query-params))
           query-params)))}))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

