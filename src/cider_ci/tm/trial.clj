; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.trial
  (:require
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms.conversion :as rdbms.conversion]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

(defonce conf (atom nil))

;#### update trial ############################################################
(defn send-update-notification [id]
  (messaging/publish-event 
    "trial_event_topic" 
    "update" {:id id}))

(defn update [id params]
  (with/logging 
    (let [table-metadata (-> @conf (:ds) (:table-metadata) (:trials))
          update-params (rdbms.conversion/convert-parameters 
                          table-metadata
                          (select-keys 
                            params
                            [:state :started_at :finished_at :error :scripts]))]
      (logging/debug update-params)
      (jdbc/update! (:ds @conf)
                    :trials update-params
                    ["id = ?::UUID" id])
      (send-update-notification id))))


;#### sql helpers #############################################################
(def sql-script-sweep-pending
  " json_array_length(scripts) > 0
  AND trials.created_at < (SELECT now() - 
  (SELECT max(trial_scripts_retention_time_days) FROM timeout_settings) 
  * interval '1 day') ")

(def sql-in-dispatch-timeout 
  " trials.created_at < (SELECT now() - 
  (SELECT max(trial_dispatch_timeout_minutes)  FROM timeout_settings) 
  * interval '1 Minute') ")

(def sql-in-terminal-state-timeout 
  " trials.created_at < (SELECT now() - 
  (SELECT max(trial_end_state_timeout_minutes)  FROM timeout_settings) 
  * interval '1 Minute') ")

(def sql-not-finished
  " state IN ('pending','dispatching','executing') ")

(def sql-to-be-dispatched
  " state = 'pending' ")


;#### initialize ##############################################################
(defn initialize [new-conf]
  (reset! conf new-conf))


;#### debug ###################################################################
; (debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


