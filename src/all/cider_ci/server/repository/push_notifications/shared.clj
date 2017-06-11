; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.push-notifications.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.repository.push-notifications.db-schema :as db-schema]
    [cider-ci.server.repository.state :as state]
    [clj-time.core :as time]
    [schema.core :as schema]
    )
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]))


(defn db-update-push-notification [id fun]
  (state/update-in-repository
    id (fn [repository]
         (let [updated-repo
               (-> repository
                   (update-in [:push-notification] fun)
                   (update-in [:push-notification] #(assoc % :updated_at (time/now))))]
           (schema/validate db-schema/schema (:push-notification updated-repo))
           updated-repo))))

(defn db-get-push-notification [id]
  (-> state/get-db :repositories (keyword id) :push-notification))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
