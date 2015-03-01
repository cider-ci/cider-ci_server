; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
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
    [cider-ci.utils.config :as config]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

(defn get-db-spec []
  (let [conf (config/get-config)]
    (deep-merge 
      (or (-> conf :database ) {} )
      (or (-> conf :services :dispatcher :database ) {} ))))


(defn -main [& args]
  (with/logging 
    (config/initialize ["../config/config_default.yml" "./config/config_default.yml" "./config/config.yml"])
    (let [conf (config/get-config)]
      (nrepl/initialize (-> conf :services :dispatcher :nrepl))
      (rdbms/initialize (get-db-spec))
      (messaging/initialize (:messaging conf))
      (http/initialize conf)
      (task/initialize)
      (sync-trials/initialize conf)
      (web/initialize conf)
      (dispatch/initialize) 
      (sweep/initialize))))

