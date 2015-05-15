; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile
  (:require 
    [cider-ci.builder.dotfile.inclusion :as inclusion]
    [cider-ci.builder.repository :as repository]
    [cider-ci.utils.map :as map]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    ))


(declare map-to-arrays)

(defn map-key-val-to-array [k spec]
  (if-let [moa (spec k)]
    (assoc spec k (map map-to-arrays (map/convert-to-array moa)))
    spec)) 


(defn map-to-arrays [spec]
  (->> spec
       (map-key-val-to-array :jobs)
       (map-key-val-to-array :tasks)
       (map-key-val-to-array :subcontexts)))

(defn- get-dotfile_ [tree-id]
  (->> 
    (repository/get-path-content tree-id ".cider-ci.yml")
    (inclusion/include tree-id)
    map-to-arrays
    ))

(def get-dotfile (memo/lru #(get-dotfile_ %)
            :lru/threshold 500))

(def get-dotfile get-dotfile_)

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

