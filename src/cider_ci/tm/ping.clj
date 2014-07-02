; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.ping
  (:require
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [cider-ci.tm.executor :as executor-entity]
    ))


(def conf (atom nil))


(defn to-be-pinged []
  (jdbc/query (:ds @conf)
              ["SELECT * FROM executors 
               WHERE enabled = 't' 
               AND ( last_ping_at < (now() - interval '30 Seconds') OR last_ping_at IS NULL)"]))


(defn ping-executors []
  (doseq [executor (to-be-pinged)]
    (logging/debug "pinging"  executor)
    (try 
      (let [response (http-client/post 
                       (executor-entity/ping-url executor)
                       {:insecure? true
                        :content-type :json
                        :accept :json 
                        :socket-timeout 1000  
                        :conn-timeout 1000 
                        :body (json/write-str {})})]
        (logging/debug "response: " response)
        (jdbc/execute! (:ds @conf)
          ["UPDATE executors SET last_ping_at = now() WHERE executors.id = ?" (:id executor)]))
      (catch Exception e (logging/warn e))))) 

(def done (atom false))

(defn start []
  (logging/info "starting executor.ping service")
  (reset! done false)
  (future 
    (loop []
      (Thread/sleep 1000)
      (when-not @done
        (ping-executors)
        (recur)))))

(defn stop []
  (logging/info "stopping executor.ping service")
  (reset! done true))

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start))
