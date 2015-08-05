; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.project-configuration
  (:require
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.project-configuration.expansion :as expansion]
    [cider-ci.repository.project-configuration.shared :as shared :refer [get-content]]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [ring.util.response :refer [charset]]
    ))


(def ^:private CONFIG-FILES-ALTERNATIVES
  ["cider-ci.yml" ".cider-ci.yml"
   "cider-ci.json" ".cider-ci.json"])

(defn- get-content-or-nil [id path]
  (try (get-content id path)
       (catch clojure.lang.ExceptionInfo e
         (case (-> e ex-data :status )
           404 nil
           (throw e)))))

(defn- get-either-configfile-content [id]
  (shared/find-repo-for-id! id)
  (loop [config-files-alternatives CONFIG-FILES-ALTERNATIVES]
    (or (get-content-or-nil id (first config-files-alternatives))
        (if-let [rest-alternatives (-> config-files-alternatives rest seq)]
          (recur rest-alternatives)
          (throw (ex-info (str "Neither configuration file "
                               CONFIG-FILES-ALTERNATIVES " was found.\n\n")
                          {:status 404}))))))

;(get-either-configfile-content "b0c4d792440a24c766b9535db95bcd18426437dc")

(defn- build-project-configuration_unmemoized [id]
  (->> (get-either-configfile-content id)
       (expansion/expand id)))


(def build-project-configuration
  (memo/lru #(build-project-configuration_unmemoized %)
            :lru/threshold 500))

; disable caching for now
(def build-project-configuration build-project-configuration_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
