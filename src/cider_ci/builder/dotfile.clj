; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile
  (:require 
    [cider-ci.builder.expansion :as expansion]
    [cider-ci.builder.repository :as repository]
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as logging]
    ))


(defn- get-dotfile_ [tree-id]
  (->> 
    (repository/get-path-content tree-id "/.cider-ci.yml")
    (expansion/expand tree-id)))

(def get-dotfile (memo/lru #(get-dotfile_ %)
            :lru/threshold 500))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

