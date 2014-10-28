; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.main
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.storage.shared :as shared]
    [cider-ci.storage.sweeper :as sweeper]
    [cider-ci.storage.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fsutils]
    ))


(defonce conf (atom {}))

(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "conf.yml"]))

(defn create-dirs [stores]
  (doseq [store stores]
    (let [directory-path (:file_path store)]
      (with/suppress-and-log-error
        (logging/debug "mkdirs " directory-path)
        (fsutils/mkdirs directory-path)))))

(defn -main [& args]
  (with/logging
    (read-config)
    (nrepl/initialize (:nrepl @conf))
    (http/initialize (select-keys @conf [:basic_auth]))
    (create-dirs (:stores @conf))
    (rdbms/initialize (get-db-spec))
    (auth/initialize (select-keys @conf [:session :basic_auth]))
    (shared/initialize {})
    (web/initialize (select-keys @conf [:http_server :stores]))
    (sweeper/initialize (select-keys @conf [:stores]))
    ))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


