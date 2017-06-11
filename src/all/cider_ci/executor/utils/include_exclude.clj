; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.utils.include-exclude
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
(:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn- matches-s? [s pattern]
  (re-find (re-pattern pattern) s))

(defn- include-match-includes? [s match-options]
  (if-let [include-match (:include_match match-options)]
    (matches-s? s include-match)
    true))

(defn- exclude-match-not-excludes? [s match-options]
  (if-let [exclude-match (:exclude_match match-options)]
    (not (matches-s? s exclude-match))
    true))

(defn passes? [s match-options]
  (if (= match-options true)
    true
    (and (include-match-includes? s match-options)
         (exclude-match-not-excludes? s match-options))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
