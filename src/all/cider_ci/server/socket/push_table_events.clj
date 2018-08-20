; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.socket.push-table-events
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.socket.shared :refer [user-clients* chsk-send!]]
    [cider-ci.server.utils.table-events :as table-events]

    [clojure.core.async :as async]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(def subscribed-tables ["branches" "projects"])


(defn push-to-client
  ([user-client-id event]
   (chsk-send! user-client-id
               [:cider-ci/entity-event event])))

(defn push-to-clients [event]
  (doseq [[user-client-id _] @user-clients*]
    (push-to-client
      user-client-id
      (case (:table_name event)
        "branches" event
        (dissoc event :data_old :data_new :data_diff)))))


(defonce chan* (atom nil))

(defn de-init []
  (when-let [c @chan*]
    (doseq [table-name subscribed-tables]
      (async/unsub table-events/pub table-name c))))

(defn init []
  (let [c (reset! chan* (async/chan (async/sliding-buffer 10000)))]
    (doseq [table-name subscribed-tables]
      (table-events/subscribe c table-name))
    (async/go-loop []
                   (when-let [event (async/<! c)]
                     (push-to-clients event)
                     (recur)))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
