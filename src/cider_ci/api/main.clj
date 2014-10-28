; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.main
  (:gen-class)
  (:require 
    [cider-ci.api.web :as web]
    [cider-ci.auth.core :as auth]
    [cider-ci.api.resources :as resources]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clojure.tools.logging :as logging]
    )
  (:import 
    [org.jruby.embed InvokeFailedException ScriptingContainer]
    ))


(defonce conf (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "conf.yml"]))


(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main
  [& args]
  (read-config)
  (with/logging 
    (rdbms/initialize (get-db-spec))
    (messaging/initialize (:messaging @conf))
    (nrepl/initialize (:nrepl @conf))
    (auth/initialize (select-keys @conf [:session :basic_auth]))
    (web/initialize (select-keys @conf [:http_server :basic_auth]))
    (resources/initialize (select-keys @conf [:api_service :storage_service]))
    )
  nil)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
