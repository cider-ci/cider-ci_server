; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.state.repositories
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.state.db :as db]
    [cider-ci.repository.state.shared :refer :all]
    [cider-ci.repository.branch-updates.db-schema :as branch-updates.db-schema]
    [cider-ci.repository.fetch-and-update.db-schema :as fetch-and-update.db-schema]
    [cider-ci.repository.push-hooks.db-schema :as push-hooks.db-schema]
    [cider-ci.repository.push-notifications.db-schema :as push-notifications.db-schema]
    [cider-ci.repository.status-pushes.db-schema :as status-pushes.db-schema]

    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.row-events :as row-events]

    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    )

  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(defn update-repositories []
  (->> ["SELECT * from repositories"]
       (jdbc/query (rdbms/get-ds))
       (map (fn [repo] [(-> repo :id keyword) repo]))
       (into {})
       (swap! db/db update-rows-in-db :repositories
              {:branch-updates (branch-updates.db-schema/default)
               :fetch-and-update (fetch-and-update.db-schema/default)
               :push-notification (push-notifications.db-schema/default)
               :push-hook (push-hooks.db-schema/default)
               :status-pushes (status-pushes.db-schema/default)})))

(def ^:private last-processed-repository-event (atom nil))

(defdaemon "update-repositories" 1
  (row-events/process "repository_events" last-processed-repository-event
                      (fn [_] (update-repositories))))

(defn initialize []
  (update-repositories)
  (start-update-repositories))
