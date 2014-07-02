; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.exception
  (:require 
    [clojure.stacktrace :as stacktrace]
    ))


(defn expand-to-seq [^Throwable tr]
  (cond
    (instance? java.sql.SQLException tr)
    (doall (iterator-seq (.iterator tr)))
    :else [tr]))


(defn trace-string-seq [ex]
  (map (fn [e] (with-out-str (stacktrace/print-trace-element e)))
       (.getStackTrace ex)
       ))

;(trace-string-seq (IllegalStateException. "asdfa"))

(defn filter-trace-string-seq [ex-seq]
  (filter 
    #(re-matches #".*cider[-_]ci.*" %)
    ex-seq))



;(filter-trace-string-seq (trace-string-seq (IllegalStateException. "asdfa")))

(defn stringify [^Throwable tr]
  (map 
    (fn [ex]
      (str [ (with-out-str (stacktrace/print-throwable ex))
            (filter-trace-string-seq (trace-string-seq ex))]))
    (expand-to-seq tr)))

;(stringify (IllegalStateException. "A"))
;(stringify (java.sql.SQLException. "SQ"))
