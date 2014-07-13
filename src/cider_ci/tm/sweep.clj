; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.sweep
  (:require
    [cider-ci.tm.trial :as trial]
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
  (jdbc/execute! 
    (:ds @conf)
    [ (str "UPDATE trials SET scripts = '[]'
           WHERE " trial/sql-script-sweep-pending) ]))

(defn sweep-in-dispatch-timeout []
  (logging/debug sweep-in-dispatch-timeout)
  (doseq [id (->> (jdbc/query 
                    (:ds @conf)
                    [ (str "SELECT id FROM trials
                           WHERE " trial/sql-to-be-dispatched
                           " AND " trial/sql-in-dispatch-timeout)])
                  (map #(:id %)))]
    (logging/debug "set failed due to dispatch timeout " id)
    (trial/update id {:state "failed" :error "dispatch timeout"}) ; TODO -> aborted
    ))

(defn sweep-in-end-state-timeout []
  (logging/debug sweep-in-end-state-timeout)
  (doseq [id (->> (jdbc/query 
                    (:ds @conf)
                    [ (str "SELECT id FROM trials
                           WHERE " trial/sql-not-finished
                           " AND " trial/sql-in-end-state-timeout)])
                  (map #(:id %)))]
    (logging/debug "set failed due to dispatch timeout " id)
    (trial/update id {:state "failed"}) ; TODO -> aborted
    ))


;#### daemon ##################################################################


(defonce _stop (atom (fn [])))


(defn stop []
  "Stops the daemonized runner.
  Will block until the runner is done. 
  No-op if the runner was never started."
  (@_stop)
  )

(defn start []
  "Starts a new daemonized runner.
   Calls stop before!"
  (stop)
  (let [done (atom false)
        runner (future (logging/debug "start looping")
                       (loop []
                         (Thread/sleep 1000)
                         (when-not @done
                           (with/suppress-and-log-error
                             (logging/debug "looping")
                             (sweep-scripts)
                             (sweep-in-dispatch-timeout)
                             (sweep-in-end-state-timeout))
                           (recur))))]
    (reset! _stop (fn []
                   (logging/debug "stopping")
                   (reset! done true)
                   @runner))))


;#### initialize ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start)
  )


