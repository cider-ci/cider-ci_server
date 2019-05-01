; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;
(ns cider-ci.executor.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:gen-class)
  (:require

    [cider-ci.constants :refer [WORKING-DIR]]
    [cider-ci.env :as env]
    [cider-ci.executor.accepted-repositories :as accepted-repositories]
    [cider-ci.executor.directories :as directories]
    [cider-ci.executor.main-config :as main-config]
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
    [cider-ci.utils.url.http :refer [parse-base-url]]
    [cider-ci.utils.url.nrepl]

    [yaml.core :as yaml]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]
    [me.raynes.fs :as clj-fs]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown]
    )
  (:import
    [java.io File]
    ))



(def common-cli-options
  [["-h" "--help"]

   ])

; should be good for now; set the rest es fix defaults for now ...

;(-main "--nrepl-enabled" "yes")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; run  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-base-url "http://loclahost:8883?enabled=false")

(def default-nrepl-url "nrepl://localhost:7883?enabled=false")

(def run-cli-options
  [["-h" "--help"]
   [nil "--show-config" "Initialize and show the configuration"]
   [nil "--http-base-url HTTP_BASE_URL" default-base-url
    :default (let [url (or (-> (System/getenv) (get "HTTP_BASE_URL" nil) presence)
                           default-base-url )]
               (parse-base-url url))
    :parse-fn parse-base-url]
   ["-b" "--server-base-url SERVER_BASE_URL" "Base URL of the Cider-CI Server"
    :default (parse-base-url "http://localhost:8888/cider-ci")
    :parse-fn parse-base-url
    :validate [#(or (= (:context %) nil)
                    (re-matches #"\/.*[^/]" %))
               "The context must be nil or be a not empty path starting with a `/`"]
    ]
   ["-n" "--nrepl-url NREPL_URL" default-nrepl-url
    :default (let [url (or (-> (System/getenv) (get "NREPL_URL" nil) presence)
                           default-nrepl-url)]
               (cider-ci.utils.url.nrepl/dissect url))
    :parse-fn cider-ci.utils.url.nrepl/dissect
    :validate [#(-> % :bind presence) "The `bind` (aka. host) part must be present"
               #(-> % :port presence) "The `port` part must be present"]]
   ["-r" "--repository-cache-dir REPOSITORY_CACHE_DIR" "Path to where repositories are stored"
    :default (-> (System/getenv)
                 (get "REPOSITORY_CACHE_DIR"
                      (clojure.string/join
                        File/separator
                        [WORKING-DIR "tmp" "repository-cache"]))
                 clj-fs/absolute clj-fs/normalized str)]
   ["-t" "--token TOKEN" "Token used to authenticate against the Cider-CI Server"
    :default (-> (System/getenv) (get "TOKEN" nil))]
   ["-w" "--working-dir WORKING-DIR" "Directory where projects will be unpacked and execution takes place"
    :default (-> (System/getenv)
                 (get "WORKING-DIR"
                      (clojure.string/join
                        File/separator
                        [WORKING-DIR "tmp" "working-dir"]))
                 clj-fs/absolute clj-fs/normalized str)]])

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
    (when (-> options :http-base-url :enabled)
      (http/initialize (:http-base-url options)))
    (when (-> options :nrepl-url :enabled)
      (nrepl/initialize (-> options :nrepl-url)))

;    (run-initialize-config options)
;    (directories/initialize)
;    (traits/initialize)
;    (sync/initialize)
;    (trials.state/initialize)
;    (cider-ci.executor.trials.working-dir-sweeper/initialize)
    ))

(defn run-main [common-options & args]
  (let [{:keys
         [options arguments errors summary]} (parse-opts
                                               args run-cli-options
                                               :in-order true)
        options (deep-merge common-options options)]
    (cond
      (:help options) (println (run-usage summary {:options options
                                                   :errors errors}))
      (:show-config options) (do
                               (println (run-usage summary
                                                   {:options options
                                                    :errors errors}))
                               (main-config/run-initialize-config options)
                               (pprint (get-config)))
      :else (run options))))

;(-main "run" "--http-base-url" "http://localhost:8883?enabled=yes")
;(-main "run" "--http-base-url" "http://localhost:8883/executor?enabled=yes")

;(-main "run" "--nrepl-url" "nrepl://localhost:7883?enabled=yes" "--show-config")
;(-main "run" "--nrepl-url" "nrepl://localhost:7883?enabled=yes")


;(-main "run" "-h")
;(-main "run" "--http-base-url" "http://localhost:8883/executor" "--show-config")
;(-main "run" "--working-dir" "/tmp" "--server-base-url" "http://SOMEHOST:1234/CTX" "--show-config")
;(-main "run" "--working-dir" "/tmp" "--http-base-url" "http://SOMEHOST:1234/CTX?enabled=false" "--show-config")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main-usage [options-summary & more]
  (->> ["Cider-CI Executor "
        ""
        "usage: cider-ci executor [<opts>] SCOPE [<scope-opts>] [<args>]"
        ""
        "Scopes:"
        "generate-config-file - generate a new config file"
        "generate-key-pair - generate a new key pair"
        "run - start the executor "
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
  (let [{:keys [options arguments errors summary]}
        (parse-opts args common-cli-options :in-order true)
        pass-on-args (->> [options (rest arguments)]
                          flatten (into []))]
    ;(println (main-usage summary {:args args :arguments arguments :errors errors :options options :pass-on-args pass-on-args}))
    (cond
      (:help options) (println (main-usage summary {:args args :options options}))
      :else (case (-> arguments first keyword)
              :generate-config-file (apply main-config/generate-config-file pass-on-args)
              :run (apply run-main pass-on-args)
              (apply run-main pass-on-args)))))


;(rest [])
;(flatten [:x '()])
;(rest [1 2 3])
;(flatten [1 [2 3]])
;(-main "run" "-h")

;### Debug ####################################################################
(debug/debug-ns *ns*)
;(debug/debug-ns 'clojure.tools.cli)
;(debug/debug-ns 'cider-ci.utils.config)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
