; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.run
  (:refer-clojure :exclude [str keyword])

  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require

    [cider-ci.constants :refer [WORKING-DIR RUN-DEFAULTS]]
    [cider-ci.env]

    [cider-ci.server.utils.table-events :as table-events]
    [cider-ci.server.projects]

    [cider-ci.server.builder.main]
    [cider-ci.server.dispatcher.main]
    [cider-ci.server.executors]
    [cider-ci.server.migrations :as migrations]
    [cider-ci.server.repository.main]
    [cider-ci.server.routes :as routes]
    [cider-ci.server.socket]
    [cider-ci.server.state]
    [cider-ci.server.status.back :as status] 
    [cider-ci.server.storage.main]
    [cider-ci.utils.app :as app]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.fs :refer [system-path]]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.nrepl]
    [cider-ci.utils.rdbms :as ds :refer [extend-pg-params]]
    [cider-ci.utils.url.http :as http-url :refer [parse-base-url]]
    [cider-ci.utils.url.jdbc :as jdbc-url]
    [cider-ci.utils.url.nrepl]

    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]
    [crypto.random]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [java.io File]
    ))


;(cheshire.core/generate-string {})

;;; config ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




(def config-defaults
  {:dispatcher {:timeout "30 Minutes",
                :storm_delay_default_duration "1 Seconds"}
   :git_proxy_enabled false,
   :trial_retention_duration "100 Years",
   :max_concurrent_fetch_and_updates 3,
   :hostname "localhost",
   :repository_service_advanced_api_edit_fields true,
   :secret "secret",
   :github_authtoken nil,
   :status_pushes_name_prefix nil,
   :task_retention_duration "100 Years",
   :job_retention_duration "100 Years",
   :status_limits {:memory {:max_usage 0.95, :min_free 10485760}},
   :session {:max_lifetime "7 days"}})

;;; http ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-http [options]
  (let [config (:base-url options)
        context (:context config)]
    {:server (cider-ci.utils.http-server/start
               (dissoc config :context)
               (routes/init (:secret options)))
     :context context}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn run [options]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    (let [secret (:secret options)
          status (status/init)
          app-handler (routes/init secret)
          ds (ds/init (:database-url options) (:health-check-registry status))]
      ;(config/initialize
      ;  {:defaults config-defaults
      ;   :resource-names []
      ;   :filenames []
      ;   :db-tables {:settings {:global []
      ;                          :dispatching [:dispatching]}}
      ;   :overrides (select-keys options [:attachments-path :secret :repositories-path :base-url])
      ;   })
      
      (table-events/init)
      (cider-ci.server.projects/init ds)
      (http-server/start (:http-base-url options) app-handler)
      ; TODO (re-)enable
      ;(when-let [params (:nrepl-url options)] (cider-ci.utils.nrepl/initialize params))
      ;(cider-ci.server.repository.main/initialize)
      ;(cider-ci.server.builder.main/initialize)
      ;(cider-ci.server.dispatcher.main/initialize)
      ;(cider-ci.server.storage.main/initialize)
      ;(cider-ci.server.executors/initialize)
      ;(cider-ci.server.state/initialize)
      (cider-ci.server.socket/initialize)
      )))


(def parse-nrepl-url cider-ci.utils.url.nrepl/dissect)

;; cli, main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn env-or-default [kw]
  (or (-> (System/getenv) (get (str kw) nil) presence)
      (get RUN-DEFAULTS kw nil)))

(def cli-options
  [["-a" "--attachments-path ATTACHMENTS_PATH"
    :default (-> (System/getenv) (get "ATTACHMENTS_PATH"
                                      (clojure.string/join File/separator [WORKING-DIR "data" "attachments"])))]
   ["-h" "--help"]
   ["-b" "--http-base-url CIDER_CI_HTTP_BASE_URL"
    (str "default: " (:CIDER_CI_HTTP_BASE_URL RUN-DEFAULTS))
    :default (http-url/parse-base-url (env-or-default :CIDER_CI_HTTP_BASE_URL))
    :parse-fn http-url/parse-base-url]
   ["-d" "--database-url LEIHS_DATABASE_URL"
    (str "default: " (:CIDER_CI_DATABASE_URL RUN-DEFAULTS))
    :default (-> (env-or-default :CIDER_CI_DATABASE_URL)
                 jdbc-url/dissect extend-pg-params)
    :parse-fn #(-> % jdbc-url/dissect extend-pg-params)]
   ["-n" "--nrepl-url NREPL_URL"
    :default (-> (System/getenv) (get "NREPL_URL" "nrepl://localhost:7881?enabled=false"))
    :parse-fn parse-nrepl-url
    :validate[#(-> % :bind presence) "The `bind` (aka. host) part must be present"
              #(-> % :port presence) "The `port` part must be present"
              #(-> % :enabled presence) "The `enabled` query parameter must be present and evaluate to a boolean"]]
   ["-r" "--repositories-path REPOSITORIES_PATH"
    :default (-> (System/getenv) (get "REPOSITORIES_PATH"
                                      (clojure.string/join File/separator [WORKING-DIR "data" "repositories"])))]
   ["-s" "--secret SECRET"
    :default (or (-> (System/getenv) (get "CIDER_CI_SERVER_SECRET" nil))
                 (when (= cider-ci.env/env :dev) "secret")
                 (crypto.random/base32 15))
    :parse-fn identity
    :validate [#(-> % presence boolean) "Must be present and not empty"]]])

(defn usage [options-summary & more]
  (->> ["Cider-CI Server Migrations "
        ""
        "usage: cider-ci server run [<opts>] [<args>]"
        ""
        "OPTIONS:"
        options-summary
        ""
        "NOTES:"
        "Every option value can also be given as an environment variable."
        "Command line options will override environment variables."
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(defn canonicalize-options [options]
  (->> options
       (map (fn [[k v]]
              (cond
                (and (= k :base-url) (string? v)) [k (parse-base-url v)]
                (and (= k :nrepl-url) (string? v)) [k (parse-nrepl-url v)]
                :else [k v])))
       (into {})))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [_] (System/exit -1))
     }
    (let [{:keys [options arguments errors summary]} (parse-opts
                                                       args cli-options
                                                       :in-order true)
          options (canonicalize-options options)
          print-summary #(println (usage summary {:args args
                                                  :options options
                                                  :errors errors}))]
      (cond
        (seq errors)  (print-summary)
        (:help options) (print-summary)
        :else (do (print-summary)
                  (run options))))))


;(-main)
;(-main "-h")
;(-main "-n" "nrepl://localhost:7881?enabled=yes")
;(-main "-h" "-b" "http://localhost/")
;(-main "-b" "https://foo.bar/baz")


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.tools.cli)
;(debug/debug-ns *ns*)
