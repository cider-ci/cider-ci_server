;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.util
  (:require 
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-uuid]
    [clojure.data.json :as json]
    ))

(defn builds-or-jobs [dotfile-map]
  (if (and (:builds dotfile-map) (:jobs dotfile-map))
    (throw (IllegalArgumentException. "The dotfile-map must contain :jobs or :maps execlusively")))
  (or (:builds dotfile-map) (:jobs dotfile-map)))

(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn id-hash [data]
  (clj-uuid/v5 clj-uuid/+null+ (json/write-str data)))

(defn idid2id [id1 id2]
  (clj-uuid/v5 clj-uuid/+null+ (str id1 id2)))



