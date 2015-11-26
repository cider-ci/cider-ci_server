; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.root
  (:require
    [cider-ci.api.json-roa.links :as json-roa.links]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))

(defn build [request]
  (let [context (:context request)]
    {:name "Root"
     :self-relation (json-roa.links/root context)
     :relations
     {
      :commits (json-roa.links/commits context)
      :jobs (json-roa.links/jobs context)
      :job (json-roa.links/job context)
      :job-specification  (json-roa.links/job-specification context)
      :task (json-roa.links/task context)
      :tasks (json-roa.links/tasks context)
      :task-specification  (json-roa.links/task-specification context)
      :trial (json-roa.links/trial context)
      :tree-attachment-data-stream (json-roa.links/tree-attachment-data-stream
                                     request "{tree_id}" "{path}")
      :create_job (json-roa.links/create-job context)
      }
     }))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
