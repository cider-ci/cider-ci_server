; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.task-specification
  (:require
    [cider-ci.api.json-roa.links :as links]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        id (-> response :body :id)]
    {:name "Task-Specification"
     :self-relation (links/task-specification context id)
     :relations
     {:tasks (links/tasks context {:task_specification_id id})
      }}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

