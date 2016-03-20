; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
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
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
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
          (throw (ex-info "Project Configuration Error"
                          {:status 404
                           :title "Project Configuration Error"
                           :description
                           (str "Neither configuration file "
                                (clojure.string/join ", " CONFIG-FILES-ALTERNATIVES)
                                " was found.")}))))))

;(get-either-configfile-content "b0c4d792440a24c766b9535db95bcd18426437dc")

(defn- build-project-configuration_unmemoized [id]
  (->> (get-either-configfile-content id)
       (expansion/expand id)))

(def build-project-configuration
  (memo/lru #(build-project-configuration_unmemoized %)
            :lru/threshold 500))

; disable caching for now
;(def build-project-configuration build-project-configuration_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
