; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.main
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
    [clojure.tools.logging :as logging]
    )
  (:import 
    [org.jruby.embed InvokeFailedException ScriptingContainer]
    ))


(defonce conf (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "/etc/api-v1/conf.yml" 
          "conf.yml"]))


(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main
  [& args]
  (read-config)
  (let [ds (rdbms/create-ds (get-db-spec))]
    (nrepl/initialize (:nrepl @conf))
    (auth/initialize (assoc (select-keys @conf [:session :basic_auth]) 
                            :ds ds))
    (web/initialize (select-keys @conf [:web :basic_auth]))
    (resources/initialize (assoc (select-keys @conf [:web :storage_manager_server]) :ds ds) )
    ))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
