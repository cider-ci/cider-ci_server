; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.main
  (:require 
    [cider-ci.sm.sweeper :as sweeper]
    [cider-ci.sm.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fsutils]
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

(defn create-dirs [stores]
  (doseq [store stores]
    (let [directory-path (:file_path store)]
      (with/suppress-and-log-error
        (logging/debug "mkdirs " directory-path)
        (fsutils/mkdirs directory-path)))))

(defn -main [& args]
  (logging/debug [-main args])
  (read-config)
  (create-dirs (:stores @conf))
  (let [ds (rdbms/create-ds (get-db-spec))]
    (web/initialize (conj (select-keys @conf [:web :stores])
                          {:ds ds}))
    (sweeper/initialize (conj (select-keys @conf [:web :stores])
                          {:ds ds}))
    ))
