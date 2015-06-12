; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile
  (:require 
    [cider-ci.builder.dotfile.inclusion :as inclusion]
    [cider-ci.builder.repository :as repository]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    ))

(defn- get-dotfile_ [tree-id]
  (->> 
    (repository/get-path-content tree-id ".cider-ci.yml")
    (inclusion/include tree-id)))

(def get-dotfile (memo/lru #(get-dotfile_ %)
            :lru/threshold 500))

; TODO re-enable caching when done
(def get-dotfile get-dotfile_)

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

