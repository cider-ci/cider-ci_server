; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.main
  (:require 
    [cider-ci.sm.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom {}))
(defonce rdbms-ds (atom {}))

(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "/etc/storage-manager/conf.yml" 
          "conf.yml"]))

(defn -main [& args]
  (logging/debug [-main args])
  (read-config)
  (let [ds (rdbms/create-ds (get-db-spec))]
    (web/initialize (conj (select-keys @conf [:web :attachments])
                          {:ds ds}))))
