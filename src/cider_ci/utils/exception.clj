; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.exception
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.stacktrace :as stacktrace]
    [clojure.tools.logging :as logging]
    [clojure.string :as string]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)




(defn expand-to-seq [^Throwable tr]
  (cond
    (instance? java.sql.SQLException tr)
    (doall (iterator-seq (.iterator tr)))
    :else [tr]))


(defn trace-string-seq [ex]
  (map (fn [e] (with-out-str (stacktrace/print-trace-element e)))
       (.getStackTrace ex)))

;(trace-string-seq (IllegalStateException. "asdfa"))

(defn filter-trace-string-seq [ex-seq]
  (filter 
    #(re-matches #".*cider[-_]ci.*" %)
    ex-seq))



;(filter-trace-string-seq (trace-string-seq (IllegalStateException. "asdfa")))

(defn stringify [^Throwable tr]
  (string/join ", " (map 
                      (fn [ex]
                        (logging/debug {:ex ex})
                        (str [ (with-out-str (stacktrace/print-throwable ex))
                              (filter-trace-string-seq (trace-string-seq ex))]))
                      (expand-to-seq tr))))
  
;(stringify (IllegalStateException. "A"))
;(stringify (java.sql.SQLException. "SQ"))
