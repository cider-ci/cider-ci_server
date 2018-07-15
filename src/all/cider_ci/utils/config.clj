; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.config
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.config.db  :as db]

    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.duration :refer [parse-string-to-seconds]]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.core :refer [str keyword deep-merge presence]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.set :refer [difference]]
    [me.raynes.fs :as clj-fs]
    [yaml.core :as yaml]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :refer [snatch with-logging]]
    ))


(defonce ^:private conf (atom {}))

(defn get-config [] @conf)

(defonce default-opts  {:defaults {}
                        :overrides {}
                        :db-tables {}
                        :resource-names ["config_default.yml"]
                        :filenames [(system-path "." "config" "config.yml")
                                    (system-path ".." "config" "config.yml")]})

(defonce opts* (atom {}))

(defn get-opts [] @opts*)

;##############################################################################

(defn exit! []
  (System/exit -1))

;##############################################################################

(defn merge-into-conf [params]
  (when-not (= (get-config)
               (deep-merge (get-config) params))
    (let [new-config (swap! conf
                            (fn [current-config params]
                              (deep-merge current-config params))
                            params)]
      (logging/info "config changed to " new-config))))

(defn slurp-and-merge [config slurpable]
  (->> (slurp slurpable)
       yaml/parse-string
       (deep-merge config)))

(defn read-and-merge-resource-name-configs [config]
  (reduce (fn [config resource-name]
            (if-let [io-resource (io/resource resource-name)]
              (snatch {} (slurp-and-merge config io-resource))
              config))
          config (:resource-names @opts*)))

(defn read-and-merge-filename-configs [config]
  (reduce (fn [config filename]
            (if (.exists (io/as-file filename))
              (snatch {} (slurp-and-merge config filename))
              config))
          config (:filenames @opts*)))

;### read and merge ###########################################################

(defn read-configs-and-merge-into-conf []
  (-> (:defaults @opts*)
      (deep-merge (get-config))
      read-and-merge-resource-name-configs
      read-and-merge-filename-configs
      (db/read-and-merge (:db-tables @opts*))
      (deep-merge (:overrides @opts*))
      merge-into-conf))

(defdaemon "reload-config" 60 (read-configs-and-merge-into-conf))


;### Initialize ###############################################################

(defn initialize
  "Initialize the configuration state and refreshing.


  The following keys for options are supported:

  :defaults
  :overrides
  :resource-names
  :filenames

  See also default-opts in the same namespace.
  "
  [options]
  (snatch {:throwable Throwable
           :level :fatal
           :return-fn (fn [_] (exit!))}
          (let [default-opt-keys (-> default-opts keys set)]
            (assert
              (empty?
                (difference (-> options keys set)
                            default-opt-keys))
              (str "Opts must only contain the following keys: " default-opt-keys))
            (stop-reload-config)
            (Thread/sleep 1000)
            (reset! conf {})
            (let [new-opts (deep-merge default-opts options)]
              (reset! opts* new-opts)
              (read-configs-and-merge-into-conf)
              (start-reload-config)
              (db/initialize @opts* read-configs-and-merge-into-conf)))))


;### DB #######################################################################

(defn get-db-spec [service]
  (let [conf (get-config)]
    (deep-merge
      (or (-> conf :database ) {} )
      (or (-> conf :services service :database ) {}))))


;### duration #################################################################

(defn parse-config-duration-to-seconds [& ks]
  (try (if-let [duration-config-value (-> (get-config) (get-in ks))]
         (parse-string-to-seconds duration-config-value)
         (logging/warn (str "No value to parse duration for " ks " was found.")))
       (catch Exception ex
         (cond (instance? clojure.lang.IExceptionInfo ex) (throw ex)
               :else (throw (ex-info "Duration parsing error."
                                     {:config-keys ks} ex))))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
