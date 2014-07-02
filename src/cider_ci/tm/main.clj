; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.main
  (:require 
    [cider-ci.messaging.core :as messaging]
    [cider-ci.rdbms.core :as rdbms]
    [cider-ci.tm.dispatch :as dispatch]
    [cider-ci.tm.ping :as ping]
    [cider-ci.tm.sync-trials :as sync-trials]
    [cider-ci.tm.sweep :as sweep]
    [cider-ci.tm.trial :as trial]
    [cider-ci.tm.web :as web]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.config-loader :as config-loader]
    ))


(defonce conf (atom {}))
(defonce rdbms-ds (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    [conf]
    ["/etc/cider-ci/executors-service/conf" "conf"]))

(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))

;(rdbms/get-columns-metadata "trials" (get-db-spec))
;(rdbms/get-table-metadata (get-db-spec))
;(jdbc/with-db-metadata [md (get-db-spec)] (jdbc/metadata-result (.getTables md nil nil nil (into-array ["TABLE" "VIEW"]))))
;(jdbc/with-db-metadata [md (get-db-spec)] (jdbc/metadata-result (.getColumns md nil nil "trials" "")))
;(jdbc/query (get-db-spec) ["SELECT * FROM executors"])

(defn -main [& args]
  (logging/debug [-main args]) 
  (read-config)
  (let [ds (rdbms/create-ds (get-db-spec))]
    (reset! rdbms-ds ds) 
    (messaging/initialize (:messaging @conf))
    (ping/initialize {:ds @rdbms-ds})
    (trial/initialize {:ds @rdbms-ds})
    (sync-trials/initialize {:ds @rdbms-ds})
    (web/initialize (:web @conf))
    (dispatch/initialize (conj {:ds @rdbms-ds} 
                               (select-keys @conf [:http_server 
                                                   :repositories_server])))
    (sweep/initialize {:ds @rdbms-ds})

    ))

;(jdbc/query @rdbms-ds ["SELECT * FROM executors"])
