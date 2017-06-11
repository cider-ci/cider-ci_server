; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;
(ns cider-ci.executor
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:gen-class)
  (:require
    [cider-ci.env :as env]
    [cider-ci.executor.accepted-repositories :as accepted-repositories]
    [cider-ci.executor.directories :as directories]
    [cider-ci.executor.http :as http]
    [cider-ci.executor.reporter :as reporter]
    [cider-ci.executor.sync :as sync]
    [cider-ci.executor.traits :as traits]
    [cider-ci.executor.trials :as trials]
    [cider-ci.executor.trials.state :as trials.state]
    [cider-ci.executor.trials.working-dir-sweeper]
    [cider-ci.utils.config :as config :refer [get-config merge-into-conf]]
    [cider-ci.utils.fs :as fs]

    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.self :as self]

    [yaml.core :as yaml]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]
    [me.raynes.fs :as clj-fs]

    [clojure.tools.logging :as logging]
    [clj-commons-exec :as commons-exec]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown]
    ))

(defn hostname []
  ( -> (commons-exec/sh ["hostname"])
       deref :out clojure.string/trim))

(defn log-env []
  (logging/info 'current-user (System/getProperty "user.name"))
  (doseq [[k v] (System/getenv)]
    (logging/debug 'env-var k v)))

(defn default-token []
  (if (= env/env :dev)
    "DemoExecutor1234"
    nil))

(def default-traits-files
  (case (System/getProperty "os.name")
    "Linux" ["/etc/cider-ci/traits.yml"]
    []))


(def default-config
  (sorted-map
    :service :executor,
    :accepted_repositories ["^.*$"]
    :server_base_url "http://localhost:8888",
    :windows {:fsi_path "C:\\Program Files (x86)\\Microsoft SDKs\\F#\\4.0\\Framework\\v4.0\\Fsi.exe"},
    :tmp_dir "tmp",
    :server_secret "master-secret",
    :trial_retention_duration "30 Minutes",
    :name "DemoExecutor",
    :hostname (hostname),
    :secret "master-secret",
    :repositories_dir (-> "./tmp/executor_repositories" clj-fs/absolute clj-fs/normalized str),
    :working_dir (->  "./tmp/working_dir" clj-fs/absolute clj-fs/normalized str),
    :default_script_timeout "3 Minutes",
    :http {:port 8883
           :host "localhost"
           :enabled (if (= env/env :dev) true false)}
    :nrepl {:port 7883,
            :bind "localhost",
            :enabled (if (= env/env :dev) true false)},
    :reporter {:max-retries 10, :retry-factor-pause-duration "3 Seconds"},
    :self_update false,
    :basic_auth {:username (hostname),
                 :password (default-token)},
    :sync_interval_pause_duration "1 Second",
    :temporary_overload_factor 1.99,
    :exec_user {:name nil, :password nil},
    :services {:dispatcher {:http {:context "/cider-ci", :sub_context "/dispatcher", :ssl false}}},
    :traits_files default-traits-files
    :max_load (.availableProcessors(Runtime/getRuntime))))

(def options-config-mapping
  {:repo-dir [:repositories_dir]
   :working-dir [:working-dir]
   :token [:basic_auth :password]
   :http-enabled [:http :enabled]
   :nrepl-enabled [:nrepl :enabled]
   :nrepl-port [:nrepl :port]
   :nrepl-bind [:nrepl :bind]
   })

(def common-cli-options
  [["-h" "--help"]
   ["-c" "--config-file PATH"  "Path to the config file"
    :default nil ;(-> "./executor-config.yml" clj-fs/absolute clj-fs/normalized str)
    ]])

; should be good for now; set the rest es fix defaults for now ...

;(-main "--nrepl-enabled" "yes")


;;; run  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def run-cli-options
  [["-h" "--help"]
   [nil  "--http-enabled yes/no" "Start the http server or not, INSECURE!"
    :parse-fn yaml/parse-string]
   ["-s" "--server-base-url URL" "Base url of the Cider-CI Server"
    :default nil
    :validate [#(-> % presence empty? not)]
    ]
   ["-t" "--token TOKEN" "Token used to authenticate against the Cider-CI Server"]
   ["-r" "--repo-dir REPO-DIR" "Path to where repositories are stored"]
   ["-w" "--working-dir WORKING-DIR"]
   [nil  "--nrepl-enabled yes/no" "Start the nrepl server or not, INSECURE!"
    :parse-fn yaml/parse-string
    ]
   [nil  "--nrepl-port PORT" "Port number of the nrepl server"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000)]]
   [nil "--nrepl-bind INTERFACE" "Where to bind the nrepl server"]])

(defn run-usage [options-summary & more]
  (->> ["Cider-CI Executor run"
        ""
        "Start the executor."
        ""
        "Options:"
        options-summary
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(defn run [options]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [_] (System/exit -1))}
    (let [config (reduce (fn [c [k v]]
                           (if-let [ks (get options-config-mapping k)]
                             (assoc-in c ks v) c))
                         default-config options)
          overrides (reduce (fn [c [k v]]
                              (if-let [ks (get options-config-mapping k)]
                                (assoc-in c ks v) c))
                            {} options)]

      (config/initialize
        {:defaults default-config
         :filenames (if-let [path (:config-file options)]
                      [(-> path clj-fs/absolute clj-fs/normalized str)]
                      [])
         :resource-names []
         :overrides overrides})
      (log-env)
      (directories/initialize)
      (traits/initialize)
      (when (-> (get-config) :http :enabled)
        (http/initialize (-> (get-config) :http)))
      (nrepl/initialize (-> (get-config) :nrepl))
      (sync/initialize)
      (trials.state/initialize)
      (cider-ci.executor.trials.working-dir-sweeper/initialize))))

(defn run-main [common-options & args]
  (let [{:keys
         [options arguments errors summary]} (parse-opts
                                               args run-cli-options
                                               :in-order true)
        options (deep-merge common-options options)]
    (cond
      (:help options) (println (run-usage summary {:options options}))
      :else (run options))))


;;; write default config ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn write-default-config-usage [options-summary & more]
  (->> ["Cider-CI Executor write-default-config"
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

(def write-default-config-file-header
  [(str "# Cider-CI " (cider-ci.utils.self/version) " Default Executor Configuration")
   "#"
   "# Some of these defaults depend on the environment."
   "# We recommend to create a configuration file based on this file"
   "# but only including those keys/values which are intended to be "
   "# overridden."
   "# The default configuration will be merged with the configuration"
   "# given in the config-file. See also 'deep-merge' in the documentation."
   "#"])

(def write-default-config-cli-options
  [["-f" "--force" "Overwrite an existing config file"]
   ["-h" "--help"
    :default false]])

(defn write-default-config [options]
  (when (nil? (:config-file options))
    (throw (ex-info "The config-file option is missing!" options)))
  (let [file (clojure.java.io/file (:config-file options))]
    (when (and (not (:force options))
               (.exists file)) (throw (ex-info "Config file exist!" options)))
    (spit file
          (->> [write-default-config-file-header
                (yaml/generate-string default-config)]
               flatten (clojure.string/join \newline)))))

(defn write-default-config-main [common-options & args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args write-default-config-cli-options :in-order true)
        options (deep-merge common-options options)]
    (cond
      (:help options) (println (write-default-config-usage summary {:options options}))
      :else (write-default-config options))))

;(-main "write-default-config" "-h")
;(-main "-c" "default-executor-config.yml" "write-default-config" "-f")


;;; main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main-usage [options-summary & more]
  (->> ["Cider-CI Executor "
        ""
        "usage: cider-ci executor [<opts>] SCOPE [<scope-opts>] [<args>]"
        ""
        "Scopes:"
        "run - start the executor "
        "write-default-config - write the default configuration to a file"
        ""
        "Options:"
        options-summary
        ""
        ""

        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(defn -main [& args]
  (logbug.thrown/reset-ns-filter-regex #".*cider[-_]ci\.executor.*")
  (let [{:keys [options arguments errors summary]}
        (parse-opts args common-cli-options :in-order true)
        pass-on-args (->> [options (rest arguments)]
                          flatten (into []))]
    ;(println (main-usage summary {:args args :arguments arguments :errors errors :options options :pass-on-args pass-on-args}))
    (cond
      (:help options) (println (main-usage summary {:args args :options options}))
      :else (case (-> arguments first keyword)
              :write-default-config (apply write-default-config-main pass-on-args)
              :run (apply run-main pass-on-args)
              (apply run-main pass-on-args)))))


;(rest [])
;(flatten [:x '()])
;(rest [1 2 3])
;(flatten [1 [2 3]])
;(-main)

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)