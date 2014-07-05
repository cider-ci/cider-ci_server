; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.config-loader
  (:require 
    [clj-yaml.core :as yaml]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [cider-ci.utils.with :as with]
    ))


(defn read-and-merge [conf-atom filenames]
  (doseq [filepath filenames]
    (let [file (clojure.java.io/as-file filepath)]
      (when (and (.exists file) (.isFile file))
        (logging/info "trying to read config: " filepath)
        (with/suppress-and-log-warn 
          (let [config-string (slurp filepath)
                config (yaml/parse-string config-string)
                merge-fun #(conj % config)]
            (logging/debug config-string config)
            (swap! conf-atom merge-fun)
            (logging/info "merged config from " filepath)))))))
