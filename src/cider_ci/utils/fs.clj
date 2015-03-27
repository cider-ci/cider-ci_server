; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.fs
  (:require 
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clj-uuid]
    [clojure.string :as string]
    [clojure.tools.logging :as logging]
    ))

(defn directory? [path]
  (let [file (clojure.java.io/file path)] 
    (and (.exists file) 
         (.isDirectory file))))

(defn assert-directory [path]
  (when-not (directory? path)
    (throw (IllegalStateException. "Directory does not exist."))))


(defn path-proof
  "Returns a unique - whenever (str x) is unique - representation of x that can
  be safely used as a filename or as a part of a path." 
  [x]
  (str (-> x
           str 
           (string/replace #"[\W_-]+" "-")
           (string/replace #"^-" "")
           (string/replace #"-$" "")
           )
       "_"
       (clj-uuid/v5 clj-uuid/+null+ (str x))))
