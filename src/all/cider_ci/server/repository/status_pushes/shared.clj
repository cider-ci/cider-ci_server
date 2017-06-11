; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.status-pushes.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.repository.status-pushes.db-schema :as db-schema]
    [cider-ci.server.repository.state :as state]
    [schema.core :as schema]
    [honeysql.core :as sql]
    [clj-time.core :as time]
    )

  (:require
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
        [:repositories.id :repository_id]
        :repositories.git_url
        :repositories.remote_api_endpoint
        :repositories.remote_api_name
        :repositories.remote_api_namespace
        :repositories.remote_api_token
        :repositories.remote_api_type)
      (sql/modifiers :distinct)
      (sql/from :repositories)))

(defn db-update-status-pushes [id fun]
  (state/update-in-repository
    id (fn [repository]
         (let [updated-repo
               (-> repository
                   (update-in [:status-pushes] fun)
                   (update-in [:status-pushes] #(assoc % :updated_at (time/now))))]
           (schema/validate db-schema/schema (:status-pushes updated-repo))
           updated-repo))))

(defn db-update-state-to-waiting-if-idle [id]
  (db-update-status-pushes
    id (fn [status-pushes]
         (if-not (some #{"ok" "error"} (:state status-pushes))
           (assoc status-pushes :state "waiting")
           status-pushes))))

(defn db-update-state [id state]
  (db-update-status-pushes
    id (fn [status-pushes] (assoc status-pushes :state state))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
