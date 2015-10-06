; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.project-configuration
  (:require
    [cider-ci.utils.http :as http]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [clojure.data.json :as json]
    ))

(defn- get-project-configuration_unmemoized [tree-id]
  (let [url (http/build-service-url
              :repository
              (str "/project-configuration/" tree-id))
        res (try (catcher/wrap-with-log :warn (http/get url {})))
        body (:body res)]
    (json/read-str body :key-fn keyword)))

(def get-project-configuration (memo/lru #(get-project-configuration_unmemoized %)
            :lru/threshold 500))

; disable caching (temporarily)
;(def get-project-configuration get-project-configuration_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
