(ns cider-ci.executors.token
  (:refer-clojure :exclude [str keyword hash])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [crypto.random]
    [pandect.algo.sha256 :as algo.sha256]
    [clojure.data.codec.base64 :as base64]


    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )

  (:import
    [com.google.common.io BaseEncoding]
    ))

(def b32 (BaseEncoding/base32))

(defn create []
  (->> 20
       crypto.random/bytes
       (.encode b32)
       (map char)
       (apply str)))

(defn valid? [token]
  (and (<= 16 (count token) 64)
       (re-matches #"\w+=*" token)))

(defn hash [s]
  (->> s
       algo.sha256/sha256-bytes
       base64/encode
       (map char)
       (apply str)))


