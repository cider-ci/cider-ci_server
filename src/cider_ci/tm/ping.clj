; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.ping
  (:require
    [cider-ci.tm.executor :as executor-entity]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(def conf (atom nil))


;#### ping ####################################################################

(defn to-be-pinged []
  (jdbc/query (:ds @conf)
              ["SELECT * FROM executors 
               WHERE enabled = 't' 
               AND ( last_ping_at < (now() - interval '30 Seconds') OR last_ping_at IS NULL)"]))

(defn ping-executor [executor]
  (with/suppress-and-log-warn
    (let [response (http/post 
                     (executor-entity/ping-url executor)
                     {:body (json/write-str {})})]
      (jdbc/execute! (:ds @conf)
                     ["UPDATE executors SET last_ping_at = now() WHERE executors.id = ?" (:id executor)]))))

(defn ping-executors []
  (doseq [executor (to-be-pinged)]
    (ping-executor executor))) 


;#### service #################################################################

(daemon/define "ping-executors" 
  start-ping-executors
  stop-ping-executors
  1
  (ping-executors))


;#### initialize ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-ping-executors))


;#### debug ###################################################################
; (debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

