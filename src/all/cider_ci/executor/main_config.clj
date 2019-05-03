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
    [cider-ci.utils.pki :as pki]

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
    [java.net InetAddress UnknownHostException]
    ))

(def default-key-pair (memoize pki/generate-key-pair))

(def default-traits-files
  (case (System/getProperty "os.name")
    "Linux" ["/etc/cider-ci/traits.yml"]
    []))

(def hostname 
  (memoize (fn []
             (try 
               (-> (InetAddress/getLocalHost) .getHostName 
                   (clojure.string/split #"\.") first)
               (catch UnknownHostException _ nil)))))

(def tmpdir
  (System/getProperty "java.io.tmpdir"))

(def default-config
  (memoize (fn []
             (sorted-map
               :accepted_repositories ["^.*$"]
               :default_script_timeout "3 Minutes"
               :exec_user {:name nil :password nil}
               :hostname (hostname)
               :max_load (.availableProcessors(Runtime/getRuntime))
               :name (hostname)
               :private_key (-> (default-key-pair) pki/key-pair->pem-private)
               :public_key (-> (default-key-pair) pki/key-pair->pem-public)
               :reporter {:max-retries 10 :retry-factor-pause-duration "3 Seconds"}
               :repositories_dir (-> (str tmpdir File/separator (hostname) "_executor-repositories-dir")
                                     clj-fs/absolute clj-fs/normalized str)
               :self_update false
               :server_base_url "http://localhost:8881"
               :sync_interval_pause_duration "1 Second"
               :temporary_overload_factor 1.99
               :tmp_dir tmpdir
               :traits_files default-traits-files
               :trial_retention_duration "30 Minutes"
               :working_dir (-> (str tmpdir File/separator (hostname) "_executor-working-dir")
                                clj-fs/absolute clj-fs/normalized str)
               ))))   


(def options-config-mapping
  {:http-base-url [:http-base-url]
   :nrepl-url [:nrepl-url]
   :repository-cache-dir [:repositories_dir]
   :server-base-url [:server-base-url]
   :working-dir [:working_dir] })


(defn run-initialize-config [options]
  (let [overrides (reduce (fn [c [k v]]
                            (if-let [ks (get options-config-mapping k nil)]
                              (assoc-in c ks v) c))
                          {} options)]
    (logging/debug :run-initialize-config/overrides overrides)
    (config/initialize
      {:defaults (default-config)
       :filenames (if-let [path (:config-file options)]
                    [(-> path clj-fs/absolute clj-fs/normalized str)]
                    [])
       :resource-names []
       :overrides overrides})))


(defn generate-config-file-usage [options-summary & more]
  (->> ["Cider-CI Executor generate-config-file"
        ""
        "Write a initial configuration file. "
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
  [(str "# Cider-CI " (cider-ci.utils.self/version) " Initial Executor Configuration")
   "#"
   "# The derived internal configuration will be merged with the configuration"
   "# values given this file. Key value pairs given here have will override "
   "# automatically derieved values. See also 'deep-merge' in the documentation."
   "# "
   "# Remove all key/value pairs which should rather be derived automatically." 
   "#"])

(def config-file-option
  ["-c" "--config-file PATH"  "Path to the config file"
   :default nil ;(-> "./executor-config.yml" clj-fs/absolute clj-fs/normalized str)
   ])

(def cli-options
  [["-f" "--force" "Overwrite an existing config file"]
   ["-h" "--help" :default false]
   config-file-option
   ])

(defn generate-config-file [options]
  (when (nil? (:config-file options))
    (throw (ex-info "The config-file option is missing!" options)))
  (let [file (clojure.java.io/file (:config-file options))]
    (when (and (not (:force options))
               (.exists file)) (throw (ex-info "Config file exist!" options)))
    (spit file
          (->> [generate-config-file-file-header
                (yaml/generate-string (default-config))]
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



;(-main {} "-c" "./tmp/initial-executor-config.yml" "-f")
;(-main {} "-h") 


