; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.utils.config
  (:require
    [clj-yaml.core :as yaml]
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.map :refer [deep-merge]]
    [clojure.tools.logging :as logging]
    [clojure.java.io :as io]
    [cider-ci.utils.rdbms :as rdbms]
    ))


(defonce ^:private conf (atom {}))

(defn get-config [] @conf)

(defn set-config [config]
  (when-not (= config @conf)
    (reset! conf config)
    (logging/info "config changed to " conf)))

(defn read-configs-and-merge [filenames]
  (loop [config {}
         filenames filenames]
    (if-let [filename (first filenames)]
      (if (.exists (io/as-file filename))
        (recur (try (let [add-on-string (slurp filename)
                          add-on (yaml/parse-string add-on-string)]
                      (deep-merge config add-on))
                    (catch Exception e
                      (logging/warn "Failed to read " filename " because " e)
                      config))
               (rest filenames))
        (recur config (rest filenames)))
      (set-config config))))


(defonce ^:private filenames (atom nil))

(daemon/define "reload-config" start-read-config stop-read-config 1
  (read-configs-and-merge @filenames))

(defn initialize
  ([]
   (initialize ["/etc/cider-ci/config_default.yml"
                "../config/config_default.yml"
                "./config/config_default.yml"
                "/etc/cider-ci/config.yml"
                "../config/config.yml"
                "./config/config.yml"]))
  ([_filenames]
   (reset! filenames _filenames)
   (read-configs-and-merge _filenames)
   (start-read-config)))

(defn get-db-spec [service]
  (let [conf (get-config)]
    (deep-merge
      (or (-> conf :database ) {} )
      (or (-> conf :services service :database ) {} ))))


;(initialize ["./config/executor_default_config.yml" "./config/executor_config.yml"])


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
