; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.tasks
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.api.pagination :as pagination]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [query-params (-> request :query-params)
        context (:context request)
        {job-id :job-id
         state :state
         task-specification-id :task-specification-id} query-params]
    (let [ids (->> response :body :tasks (map :id))]
      {:name "Tasks"
       :self-relation (links/tasks context query-params)
       :relations (merge {:root (links/root context)}
                         (when-not (clojure.string/blank? job-id)
                           {:job (links/job context job-id) }))
       :collection (conj
                     {:relations
                      (into {}
                            (map-indexed
                              (fn [i id]
                                [(+ 1 i (pagination/compute-offset query-params))
                                 (links/task context id)])
                              ids))}
                     (when (seq ids)
                       (links/next-rel
                         (fn [query-params]
                           (links/jobs-path context query-params))
                         query-params)))})))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

