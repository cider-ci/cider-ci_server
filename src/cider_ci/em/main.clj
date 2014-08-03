; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.em.main
  (:require 
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
          "/etc/execution-manager/conf.yml" 
          "conf.yml"]))


(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main [& args]
  (read-config)
  (nrepl/initialize (:nrepl @conf))
  (let [ds (rdbms/create-ds (get-db-spec))]
    (reset! rdbms-ds ds) 
    (messaging/initialize (:messaging @conf))
    ))

