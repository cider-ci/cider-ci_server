(ns cider-ci.executors
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.executors.state :as state]
    [cider-ci.executors.web :as web]
    [cider-ci.executors.token :as token]
    ))

(def routes web/routes)
(def executors* state/db*)
(defn initialize [] (state/initialize))

(defn find-executor-by-token [token]
  (let [hash (token/hash token)]
    (->> @executors*
         (map (fn [[_ e]] e))
         (filter #(= hash (:token_hash %)))
         first)))


