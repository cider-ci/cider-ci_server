; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.branch-updates.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.repository.branch-updates.db-schema :as db-schema]
    [cider-ci.repository.state :as state]
    [clj-time.core :as time]
    [schema.core :as schema]
    )
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]))


(defn db-update-branch-updates [id fun]
  (state/update-in-repository
    id (fn [repository]
         (let [updated-repo
               (-> repository
                   (update-in [:branch-updates] fun)
                   (update-in [:branch-updates] #(assoc % :updated_at (time/now))))]
           (schema/validate db-schema/schema (:branch-updates updated-repo))
           updated-repo))))

(defn db-get-branch-updates [id]
  (-> (state/get-db) :repositories (get (keyword id)) :branch-updates))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
