; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.map
  (:require 
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))


(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))


(defn convert-to-array 
  "Converts a map of maps to an array of maps.
  The key of will become the value of the :name 
  property if it did no exists already."
  [map-or-array]
  (if (and (map? map-or-array)
           (every?  map? (map second map-or-array)))
    (map (fn [[k m]]
           (if (:name m) 
             m
             (assoc m :name k)))
         map-or-array)
    map-or-array))


;(convert-to-array {:x {:y 1}})
;(convert-to-array {:x {:name 1}})
;(convert-to-array [{:name "x"}])


