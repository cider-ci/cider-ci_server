; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.project-configuration
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.repository.git.repositories :as git.repositories]
    [cider-ci.server.repository.project-configuration.expansion :as expansion]
    [cider-ci.server.repository.project-configuration.shared :as shared :refer :all]
    [clojure.core.memoize :as memo]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [ring.util.response :refer [charset]]

    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(def ^:private CONFIG-FILES-ALTERNATIVES
  ["cider-ci_v4.yml" ".cider-ci_v4.yml"
   "cider-ci_v4.json" ".cider-ci_v4.json"
   "cider-ci.yml" ".cider-ci.yml"
   "cider-ci.json" ".cider-ci.json"])

(defn- get-content-or-nil [id path]
  (try (->> (get-content id path [])
            (parse-path-content path))
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

(defn- build-project-configuration_unmemoized [id]
  (->> (get-either-configfile-content id)
       (expansion/expand id)))

(def build-project-configuration
  (memo/lru #(build-project-configuration_unmemoized %)
            :lru/threshold 500))

; to disable caching temporarily:
;(def build-project-configuration build-project-configuration_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
