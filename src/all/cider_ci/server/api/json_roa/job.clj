; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.json-roa.job
  (:require
    [cider-ci.server.api.json-roa.links :as links]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        id (-> response :body :id)
        job-specification-id (-> response :body :job_specification_id)]
    {:name "Job"
     :self-relation (links/job context id)
     :relations
     {:tree-attachments (links/tree-attachments context id)
      :jobs (links/jobs context)
      :job-specification (links/job-specification context job-specification-id)
      :tasks (links/tasks context {:job_id id})}}))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

