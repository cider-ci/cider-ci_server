;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.util
  (:require
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-uuid]
    [clojure.data.json :as json]
    [clojure.walk :refer [postwalk]]
    ))

(defn json-key-fn [k]
  (if (keyword? k) (subs (str k) 1) (str k) ))

(defn json-write-str [data]
  (json/write-str data :key-fn json-key-fn))

(defn id-hash [data]
  (clj-uuid/v5 clj-uuid/+null+ (json-write-str data)))

(defn idid2id [id1 id2]
  (clj-uuid/v5 clj-uuid/+null+ (str id1 id2)))

(defn stringify-keys [m]
  (let [f (fn [[k v]] [(json-key-fn k) v])]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))
