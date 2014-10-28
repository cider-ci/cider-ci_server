; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.main
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.builder.expansion :as expansion]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
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
          "conf.yml"]))


(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main [& args]
  (read-config)
  (nrepl/initialize (:nrepl @conf))
  (rdbms/initialize (get-db-spec))
  (messaging/initialize (:messaging @conf))
  (tasks/initialize)
  (auth/initialize (select-keys @conf [:session :basic_auth]))
  (web/initialize (select-keys @conf [:http_server]))
  (expansion/initialize 
    (select-keys @conf [:repository_service
                        :basic_auth]))

  nil)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
