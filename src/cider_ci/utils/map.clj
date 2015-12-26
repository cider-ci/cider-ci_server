; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.map
  (:require
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))


(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))


(defn k2str [k]
  (if (keyword? k) (subs (str k) 1) (str k) ))


(defn convert-to-array
  "Converts a map of maps to an array of maps. The key becomes the value of the
  :name property if and only if :name does no exists already."
  [map-or-array]
  (if (and (map? map-or-array)
           (every?  map? (map second map-or-array)))
    (map (fn [[k m]]
           (conj m
                 {:key (k2str k)}
                 (when-not (:name m)
                   {:name (k2str k)})))
         map-or-array)
    map-or-array))

