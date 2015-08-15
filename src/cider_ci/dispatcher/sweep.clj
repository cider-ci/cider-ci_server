; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.sweep
  (:require
    [cider-ci.dispatcher.trial :as trial]
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defn sweep-in-dispatch-timeout []
  (doseq [id (->> (jdbc/query
                    (rdbms/get-ds)
                    [ (str "SELECT id FROM trials
                           WHERE " trial/sql-to-be-dispatched
                           " AND " trial/sql-in-dispatch-timeout)])
                  (map #(:id %)))]
    (catcher/wrap-with-suppress-and-log-error
      (trial/update {:id id :state "failed" :error "dispatch timeout"}) ; TODO -> aborted
      )))

(defn sweep-in-terminal-state-timeout []
  (doseq [id (->> (jdbc/query
                    (rdbms/get-ds)
                    [ (str "SELECT id FROM trials
                           WHERE " trial/sql-not-finished
                           " AND " trial/sql-in-terminal-state-timeout)])
                  (map #(:id %)))]
    (catcher/wrap-with-suppress-and-log-error
      (trial/update {:id id :state "failed" :error "terminal-state timeout"}) ; TODO -> aborted
      )))


;#### daemons ##################################################################

(daemon/define "terminal-state-timeout-sweeper"
  start-terminal-state-timeout-sweeper
  stop-terminal-state-timeout-sweeper
  1
  (logging/debug "terminal-state-timeout-sweeper")
  (sweep-in-terminal-state-timeout))

(daemon/define "dispatch-timeout-sweeper"
  start-dispatch-timeout-sweeper
  stop-dispatch-timeout-sweeper
  1
  (logging/debug "dispatch-timeout-sweeper")
  (sweep-in-dispatch-timeout))


;#### initialize ##############################################################

(defn initialize []
  (start-dispatch-timeout-sweeper)
  (start-terminal-state-timeout-sweeper)
  )

;#### debug ###################################################################
; (debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

