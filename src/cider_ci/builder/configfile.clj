; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.configfile
  (:require
    [cider-ci.builder.configfile.inclusion :as inclusion]
    [cider-ci.builder.repository :as repository]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    ))

(defn- get-configfile_unmemoized [tree-id]
  (->>
    (repository/get-path-content tree-id ".cider-ci.yml")
    (inclusion/include tree-id)))

(def get-configfile (memo/lru #(get-configfile_unmemoized %)
            :lru/threshold 500))

; disable caching (temporarily)
;(def get-configfile get-configfile_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

