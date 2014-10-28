; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.sync-trials
  (:require
    [cider-ci.dispatcher.executor :as executor-entity]
    [cider-ci.dispatcher.trial :as trial-entity]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

(def conf (atom nil))

(defn trials-to-be-synced []
  (jdbc/query (rdbms/get-ds)
    ["SELECT * FROM trials
        WHERE state IN ('executing')
        AND trials.executor_id IS NOT NULL"]))

(defn trial-request-url [executor-db trial-db]
  (str (executor-entity/base-url executor-db) "/trials/" (:id trial-db)))

(defn get-executor [id]
  (first (jdbc/query (rdbms/get-ds)
    ["SELECT * from executors WHERE id = ?" id])))

(defn check-trials []
  (doseq [trial (trials-to-be-synced)]
    (future 
      (try 
        (let [executor (get-executor (:executor_id trial))
              url (trial-request-url executor trial)
              response (http/get
                         url
                         {:insecure? true
                          :accept :json
                          :socket-timeout 3000 
                          :conn-timeout 3000})]
          ;TODO maybe implement some logic that considers the states 'success' and 'failed'
          ; maybe after we move the API from TB to IM 
          (logging/warn ["HANDLING TO BE IMPLEMENTED" check-trials response]))
        (catch Exception e 
          (logging/warn e)
          (let [trial-update {:id (:id trial) 
                              :state "failed" 
                              :error "The trial was lost on the executor."}]
            (trial-entity/update trial-update)))))))


;#### service #################################################################
(daemon/define "check-trials" 
  start-check-trials
  stop-check-trials
  60
  (check-trials))


;#### initialize ##############################################################
(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-check-trials))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


