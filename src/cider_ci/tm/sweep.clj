; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.sweep
  (:require
    [cider-ci.tm.trial :as trial]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


;#### 

(defonce conf (atom {}))


(defn sweep-scripts []
  (logging/debug sweep-scripts)
  (with/suppress-and-log-error
    (jdbc/execute! 
      (:ds @conf)
      [ (str "UPDATE trials SET scripts = '[]'
             WHERE " trial/sql-script-sweep-pending) ])))

(defn sweep-in-dispatch-timeout []
  (logging/debug sweep-in-dispatch-timeout)
  (doseq [id (->> (jdbc/query 
                    (:ds @conf)
                    [ (str "SELECT id FROM trials
                           WHERE " trial/sql-to-be-dispatched
                           " AND " trial/sql-in-dispatch-timeout)])
                  (map #(:id %)))]
    (with/suppress-and-log-error
      (logging/debug "set failed due to dispatch timeout " id)
      (trial/update id {:state "failed" :error "dispatch timeout"}) ; TODO -> aborted
      )))

(defn sweep-in-end-state-timeout []
  (logging/debug sweep-in-end-state-timeout)
  (doseq [id (->> (jdbc/query 
                    (:ds @conf)
                    [ (str "SELECT id FROM trials
                           WHERE " trial/sql-not-finished
                           " AND " trial/sql-in-end-state-timeout)])
                  (map #(:id %)))]
    (with/suppress-and-log-error 
      (logging/debug "set failed due to dispatch timeout " id)
      (trial/update id {:state "failed"}) ; TODO -> aborted
      )))


;#### daemons ##################################################################


(daemon/define "scripts-sweeper" 
  start-scripts-sweeper 
  stop-scripts-sweeper 
  (* 5 60)
  (logging/debug "scripts-sweeper")
  (sweep-scripts))

(daemon/define "end-state-timeout-sweeper" 
  start-end-state-timeout-sweeper 
  stop-end-state-timeout-sweeper 
  1
  (logging/debug "end-state-timeout-sweeper")
  (sweep-in-end-state-timeout))

(daemon/define "dispatch-timeout-sweeper" 
  start-dispatch-timeout-sweeper 
  stop-dispatch-timeout-sweeper 
  1
  (logging/debug "dispatch-timeout-sweeper")
  (sweep-in-dispatch-timeout))


;#### initialize ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-dispatch-timeout-sweeper)
  (start-end-state-timeout-sweeper)
  (start-scripts-sweeper)
  )


