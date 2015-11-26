; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.scripts
  (:require
    [camel-snake-kebab.core :refer [->snake_case ->kebab-case-keyword ->snake_case_keyword]]
    [camel-snake-kebab.extras :refer [transform-keys]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as clj-logging]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as logbug.thrown]
    [drtom.logbug.thrown :as thrown]
    )
  (:import
    [java.io InputStream]
    ))

;### helpers ###################################################################

(defn- snake-case-top-level-keys [mp]
  (->> mp
      (into [])
      (map (fn [[k v]] [(->snake_case_keyword k) v]))
      (into {})))

(defn- kebab-case-top-level-keys [mp]
  (->> mp
      (into [])
      (map (fn [[k v]] [(->kebab-case-keyword k) v]))
      (into {})))


;### create scripts ############################################################

(defn- add-key-or-name-to-script [script]
  (when-not (or (:key script) (:name script))
    (throw (ex-info "A script must have at least one of :key or :name property"
                    {:script script})))
  (merge script
         (when-not (:key script)
           {:key (:name script)})
         (when-not (:name script)
           {:name (:key script)})))

(defn create-scripts [tx trial scripts]
  (doseq [script (-> scripts convert-to-array)]
    (->> (assoc script :trial_id (:id trial))
         convert-to-array
         add-key-or-name-to-script
         snake-case-top-level-keys
         (jdbc/insert! tx :scripts))))

;### get scripts ###############################################################

(defn get-scripts [trial]
  (->> (jdbc/query (get-ds)
                   ["SELECT * FROM scripts WHERE trial_id = ?" (:id trial)])
       (map kebab-case-top-level-keys)))

;### patch script ##############################################################

(def ^:private update-permitted-keys
  #{:state :exit_status :environment_variables
    :working_dir :command
    :script_file :wrapper_file
    :finished_at :started_at :error})

(defmacro catch-with-suppress-and-log [level catch-sex & expressions]
  `(try
     ~@expressions
     (catch Throwable e#
       (clj-logging/log ~level (logbug.thrown/stringify e#))
       ~catch-sex)))

(defn remove-null-values [data]
  (->> data
      (into [])
      (filter (fn [[k v]] (not= nil v)))
      (into {})))

(defn- patch-script [trial-id script-key data]
  (catch-with-suppress-and-log
    :error {:status 500 :body "Update error, see the logs for details."}
    (-> data
        remove-null-values
        snake-case-top-level-keys
        (select-keys update-permitted-keys)
        (#(jdbc/update! (get-ds) :scripts
                        % ["trial_id = ? AND key = ? " trial-id script-key])))
    {:status 200}))

;### patch field  ##############################################################

(defn- get-current-value [trial-id script-key field]
  (-> (jdbc/query (get-ds) [(str "SELECT * "
                                 " FROM scripts WHERE trial_id = ? "
                                 " AND  key = ?") trial-id script-key])
      first
      (get (keyword field))))

(defn- build-new-value [current-value data]
  (condp instance? current-value
    String  (condp instance? data
              String (clojure.string/join [current-value data])
              InputStream (clojure.string/join [current-value (str (slurp data))]))
    Object (throw (ex-info "build-new-value is not implemented for "
                           (type data)))))

(defn- patch-field [trial-id script-key field data]
  (catch-with-suppress-and-log
    :error {:status 500 :body "Error when patching script filed, see the logs for details."}
    (let [current-value (get-current-value trial-id script-key field)
          new-value (build-new-value current-value data)]
      ;(logging/info 'PATCH-FIELD 'NEW-VALUE {:value new-value :type (type new-value)})
      (jdbc/update! (get-ds) :scripts {field (str new-value)}
                    ["trial_id = ? AND key = ? " trial-id script-key]))))

;### routes ####################################################################

(def script-routes
  (cpj/routes
    (cpj/PATCH "/trials/:id/scripts/:key"
               {{id :id key :key} :params data :body}
               (patch-script id key data))

    (cpj/PATCH "/trials/:id/scripts/:key/:field"
               {{id :id key :key field :field} :params data :body}
               (patch-field id key field data))
    ))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'patch-script)
;(debug/wrap-with-log-debug #'build-new-value)
;(debug/wrap-with-log-debug #'patch-field)
