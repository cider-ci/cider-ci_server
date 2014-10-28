; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.main
  (:require 
    [cider-ci.dispatcher.dispatch :as dispatch]
    [cider-ci.dispatcher.ping :as ping]
    [cider-ci.dispatcher.sweep :as sweep]
    [cider-ci.dispatcher.sync-trials :as sync-trials]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.trial :as trial]
    [cider-ci.dispatcher.web :as web]
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
          "/etc/cider-ci_dispatcher/conf.yml" 
          "conf.yml"]))

(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main [& args]
  (read-config)
  (nrepl/initialize (:nrepl @conf))
  (rdbms/initialize (get-db-spec))
  (messaging/initialize (:messaging @conf))
  (http/initialize (select-keys @conf [:basic_auth]))
  (ping/initialize {})
  (trial/initialize {})
  (task/initialize )
  (sync-trials/initialize {})
  (web/initialize (select-keys @conf [:http_server :basic_auth]))
  (dispatch/initialize  (select-keys @conf [:repository_service
                                            :storage_service
                                            :dispatcher_service]))
  (sweep/initialize {}))

