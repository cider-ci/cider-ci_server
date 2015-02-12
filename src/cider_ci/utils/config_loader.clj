; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.config-loader
  (:require 
    [clj-yaml.core :as yaml]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [cider-ci.utils.with :as with]
    [cider-ci.utils.map :refer [deep-merge]]
    ))


(defn read-and-merge [conf-atom filenames]
  (doseq [filename filenames]
    (logging/info "looking for " filename " config resource")
    (if-let [config-resource (clojure.java.io/resource filename)]
      (do
        (logging/info "trying to read, parse and merge " config-resource)
        (with/suppress-and-log-warn 
          (let [config-string (slurp config-resource)
                config (yaml/parse-string config-string)
                merge-fun #(deep-merge % config)]
            (logging/debug "read config" config)
            (swap! conf-atom merge-fun)
            (logging/info "merged config from " filename))))
      (logging/info filename " config not found, skipping")))
  (logging/info "read-and-merge done, result: " @conf-atom))
