; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.task
  (:require
    [cider-ci.api.json-roa.links :as links]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        id (-> response :body :id)
        task-specification-id (-> response :body :task_specification_id)]
    {:name "Task"
     :self-relation (links/task context id)
     :relations
     {:trials (links/trials context id)
      :task-specification (links/task-specification context task-specification-id)
      :job (links/job context (-> response :body :job_id)) }}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
