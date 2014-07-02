; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.with
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as clj-logging]
    [cider-ci.utils.exception :as exception]
    ))


(defmacro logging [& expressions]
  `(try
     ~@expressions
     (catch Throwable e#
       (clj-logging/error (exception/stringify e#))
       (throw e#))))

(defmacro logging-and-suppress [& expressions]
  `(try
     ~@expressions
     (catch Throwable e#
       (clj-logging/error (exception/stringify e#))
       nil)))


