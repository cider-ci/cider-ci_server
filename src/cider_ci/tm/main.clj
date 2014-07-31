; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.main
  (:require 
    [cider-ci.tm.dispatch :as dispatch]
    [cider-ci.tm.ping :as ping]
    [cider-ci.tm.sweep :as sweep]
    [cider-ci.tm.sync-trials :as sync-trials]
    [cider-ci.tm.trial :as trial]
    [cider-ci.tm.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom {}))
(defonce rdbms-ds (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "/etc/trial-manager/conf.yml" 
          "conf.yml"]))


(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main [& args]
  (read-config)
  (nrepl/initialize (:nrepl @conf))
  (let [ds (rdbms/create-ds (get-db-spec))]
    (reset! rdbms-ds ds) 
    (messaging/initialize (:messaging @conf))
    (http/initialize (select-keys @conf [:basic_auth]))
    (ping/initialize {:ds @rdbms-ds})
    (trial/initialize {:ds @rdbms-ds})
    (sync-trials/initialize {:ds @rdbms-ds})
    (web/initialize (select-keys @conf [:web :basic_auth]))
    (dispatch/initialize (conj {:ds @rdbms-ds} 
                               (select-keys @conf [:repository_manager_server
                                                   :storage_manager_server
                                                   :trial_manager_server])))
    (sweep/initialize {:ds @rdbms-ds})))

