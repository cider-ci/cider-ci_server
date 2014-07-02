; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.trial
  (:require
    [cider-ci.utils.with :as with]
    [cider-ci.messaging.core :as messaging]
    [cider-ci.rdbms.conversion :as rdbms.conversion]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom nil))


;(jdbc/db-connection (:ds conf))

;(jdbc/metadata-result (.getTables (:ds @conf) nil nil nil (into-array ["TABLE" "VIEW"])))


(defn send-update-notification [id]
  (messaging/publish-event 
    "trial_event_topic" 
    "update" {:id id}))


(defonce _update (atom nil))
(defn update [id params]
  (logging/debug update [id params])
  (reset! _update [id params])
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
      (send-update-notification id)
      )))
;(apply update @_update)

(defn initialize [new-conf]
  (reset! conf new-conf))

(def sql-script-sweep-pending
  " json_array_length(scripts) > 0
  AND trials.created_at < (SELECT now() - 
  (SELECT max(trial_scripts_retention_time_days) FROM timeout_settings) 
  * interval '1 day') ")



(def sql-in-dispatch-timeout 
  " trials.created_at < (SELECT now() - 
  (SELECT max(trial_dispatch_timeout_minutes)  FROM timeout_settings) 
  * interval '1 Minute') ")

(def sql-in-end-state-timeout 
  " trials.created_at < (SELECT now() - 
  (SELECT max(trial_end_state_timeout_minutes)  FROM timeout_settings) 
  * interval '1 Minute') ")


(def sql-not-finished
  " state NOT IN ('aborted','success','failed') ")

(def sql-to-be-dispatched
  " state = 'pending' ")

;(update {:id "a6deda0f-d881-4611-a034-a926c0470da8", :state "asdfax" :scripts [1]})
;(jdbc/update! (:ds @conf) :trials {:error "blah" :scripts (cider-ci.rdbms.conversion/convert-to-json []) } ["id = ?::UUID","a6deda0f-d881-4611-a034-a926c0470da8"])
;(first (jdbc/query (:ds @conf) ["select * from trials"]))
