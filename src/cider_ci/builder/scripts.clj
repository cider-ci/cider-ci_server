; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.scripts
  (:require

    [camel-snake-kebab.core :refer [->snake_case ->snake_case_keyword]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as logbug.thrown]
    [logbug.thrown :as thrown]
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

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'patch-script)
;(debug/wrap-with-log-debug #'remove-null-values)
;(debug/wrap-with-log-debug #'validate-token)
