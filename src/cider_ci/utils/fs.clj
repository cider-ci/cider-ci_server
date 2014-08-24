; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.fs
  (:require 
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))

(defn directory? [path]
  (let [file (clojure.java.io/file path)] 
    (and (.exists file) 
         (.isDirectory file))))

(defn assert-directory [path]
  (when-not (directory? path)
    (throw (IllegalStateException. "Directory does not exist."))))


