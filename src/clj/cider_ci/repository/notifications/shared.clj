; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.notifications.shared
  (:require
    [cider-ci.repository.notifications.github :as github]

    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(def base-query
  (-> (sql/select
        [:commits.id :commit_id]
        [:jobs.id  :job_id]
        :jobs.name
        :jobs.state
        :repositories.git_url
        :repositories.remote_api_endpoint
        :repositories.remote_api_name
        :repositories.remote_api_namespace
        :repositories.remote_api_token
        :repositories.remote_api_type)
      (sql/modifiers :distinct)
      (sql/from :repositories)
      (sql/merge-where [:<> nil :remote_api_endpoint])
      (sql/merge-where [:<> nil :remote_api_type])
      (sql/merge-where [:<> nil :remote_api_token])
      (sql/merge-where [:<> nil :remote_api_namespace])
      (sql/merge-where [:<> nil :remote_api_name])))


(defn dispatch [rows]
  (doseq [params rows]
    (case (:remote_api_type params)
      "github" (github/post-status params)
      nil)))
