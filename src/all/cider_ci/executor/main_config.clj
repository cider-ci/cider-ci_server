; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.executor.main-config
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:gen-class)
  (:require
    [cider-ci.env :as env]
    [cider-ci.utils.config :as config :refer [get-config merge-into-conf]]
    [cider-ci.utils.fs :as fs]
    [cider-ci.utils.self :as self]
    [cider-ci.constants :refer [WORKING-DIR]]

    [yaml.core :as yaml]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]
    [me.raynes.fs :as clj-fs]
    [clj-commons-exec :as commons-exec]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown]
    )
  (:import
    [java.io File]
    ))


(def default-traits-files
  (case (System/getProperty "os.name")
    "Linux" ["/etc/cider-ci/traits.yml"]
    []))

(defn hostname []
  ( -> (commons-exec/sh ["hostname"])
       deref :out clojure.string/trim))

(def default-config
  (sorted-map
  ;  :service :executor,
    :accepted_repositories ["^.*$"]
;    :windows {:fsi_path "C:\\Program Files (x86)\\Microsoft SDKs\\F#\\4.0\\Framework\\v4.0\\Fsi.exe"},
    :tmp_dir "tmp",
;    :server_secret "secret",
    :trial_retention_duration "30 Minutes",
    :name "DemoExecutor",
    :hostname (hostname),
;    :secret "secret",
    :repositories_dir nil;(-> "./tmp/executor_repositories" clj-fs/absolute clj-fs/normalized str),
    :working_dir (->  "./tmp/working_dir" clj-fs/absolute clj-fs/normalized str),
    :default_script_timeout "3 Minutes",
;    :http {:port 8883
;           :host "localhost"
;           :enabled (if (= env/env :dev) true false)}
;    :nrepl {:port 7883,
;            :bind "localhost",
;            :enabled (if (= env/env :dev) true false)},
    :reporter {:max-retries 10, :retry-factor-pause-duration "3 Seconds"},
    :self_update false,
    :sync_interval_pause_duration "1 Second",
    :temporary_overload_factor 1.99,
    :exec_user {:name nil, :password nil},
;    :services {:dispatcher {:http {:context "/cider-ci", :sub_context "/dispatcher", :ssl false}}},
    :traits_files default-traits-files
    :max_load (.availableProcessors(Runtime/getRuntime))))


(def options-config-mapping
  {:http-base-url [:http-base-url]
   :nrepl-url [:nrepl-url]
   :repository-cache-dir [:repositories_dir]
   :server-base-url [:server-base-url]
   :token [:basic_auth :password]
   :working-dir [:working_dir] })


(defn run-initialize-config [options]
  (let [overrides (reduce (fn [c [k v]]
                            (if-let [ks (get options-config-mapping k nil)]
                              (assoc-in c ks v) c))
                          {} options)]
    (logging/debug :run-initialize-config/overrides overrides)
    (config/initialize
      {:defaults default-config
       :filenames (if-let [path (:config-file options)]
                    [(-> path clj-fs/absolute clj-fs/normalized str)]
                    [])
       :resource-names []
       :overrides overrides})))


(defn generate-config-file-usage [options-summary & more]
  (->> ["Cider-CI Executor generate-config-file"
        ""
        "Write the default configuration to a file. "
        "Commonly used to retrieve an initial configuration for the executor."
        ""
        "Options:"
        options-summary
        ""

        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(def generate-config-file-file-header
  [(str "# Cider-CI " (cider-ci.utils.self/version) " Default Executor Configuration")
   "#"
   "# Some of these defaults depend on the environment."
   "# We recommend to create a configuration file based on this file"
   "# but only including those keys/values which are intended to be "
   "# overridden."
   "# The default configuration will be merged with the configuration"
   "# given in the config-file. See also 'deep-merge' in the documentation."
   "#"])

(def config-file-option
  ["-c" "--config-file PATH"  "Path to the config file"
   :default nil ;(-> "./executor-config.yml" clj-fs/absolute clj-fs/normalized str)
   ])

(def cli-options
  [["-f" "--force" "Overwrite an existing config file"]
   ["-h" "--help"
    :default false]])

(defn generate-config-file [options]
  (when (nil? (:config-file options))
    (throw (ex-info "The config-file option is missing!" options)))
  (let [file (clojure.java.io/file (:config-file options))]
    (when (and (not (:force options))
               (.exists file)) (throw (ex-info "Config file exist!" options)))
    (spit file
          (->> [generate-config-file-file-header
                (yaml/generate-string default-config)]
               flatten (clojure.string/join \newline)))))


(defn usage [options-summary & more]
  (->> ["Cider-CI Executor generate-config-file"
        ""
        ""
        "Options:"
        options-summary
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))


(defn -main [common-options & args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options :in-order true)
        options (deep-merge common-options options)]
    (cond
      (:help options) (println (generate-config-file-usage summary {:options options}))
      :else (generate-config-file options))))



;(-main "-c" "default-executor-config.yml" "-f")


