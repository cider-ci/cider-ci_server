; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.socket.push-table-events
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.socket.shared :refer [user-clients* chsk-send!]]

    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.row-events :as row-events]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(defn push-to-client
  ([user-client-id event]
   (let [push-data* (atom nil)]
     (chsk-send! user-client-id
                 [:cider-ci/entity-event event]))))

(defn push-to-clients [event]
  (doseq [[user-client-id _] @user-clients*]
    (push-to-client user-client-id event)))

(def ^:private last-processed-event (atom nil))

(defn process-event-rows []
  (row-events/process
    "events" last-processed-event
    push-to-clients))

(defdaemon "process-event-rows" 0.25
  (process-event-rows))

(defn initialize []
  (start-process-event-rows))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
