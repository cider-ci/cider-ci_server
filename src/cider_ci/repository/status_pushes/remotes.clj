; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.status-pushes.remotes
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.repository.status-pushes.remotes.github :as github]
    [cider-ci.repository.remote :refer [api-type api-access?]]
    [cider-ci.repository.status-pushes.shared :refer [db-update-state]]

    [cider-ci.utils.config :as config :refer [get-config]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn name-prefix []
  (or (-> (get-config) :status_pushes_name_prefix presence)
      (str "Cider-CI@" (:hostname (get-config)))))

(defn exend-params [params]
  (assoc params :name-prefix (name-prefix)))

(defn dispatch [rows]
  (doseq [params (->> rows (map exend-params))]
    (if-not (api-access? params)
      (db-update-state (:repository_id params) "unaccessible")
      (case (api-type params)
        "github" (github/post-status params)
        nil))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
