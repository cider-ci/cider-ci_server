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

(defmacro suppress-and-log [level & expressions]
  `(try
     ~@expressions
     (catch Throwable e#
       (clj-logging/log ~level (exception/stringify e#))
       nil)))

(defmacro suppress-and-log-debug [& expressions]
 `(suppress-and-log :debug ~@expressions))

(defmacro suppress-and-log-info [& expressions]
 `(suppress-and-log :info ~@expressions))

(defmacro suppress-and-log-warn [& expressions]
 `(suppress-and-log :warn ~@expressions))

(defmacro suppress-and-log-error [& expressions]
 `(suppress-and-log :error ~@expressions))


;(macroexpand '(suppress-and-log-warn (println "hello world")))


(defmacro log-debug-result [& expressions]
  `(let [res# (do ~@expressions)]
     (logging/debug {:result res#})
     res#))


(defmacro catch-and-fail-state 
  "Takes as the first argument the state-container, a hash wrapped inside 
  an atom. Executes the body and catches any exception thrown therein. 
  In the latter case, sets the :state property of the state-container 
  to \"failed\" and also populates the :error and :errors property.
  Returns the state-container."
  [state-container & body]
  `(try
     ~@body
     (catch Exception e#
       (let [error-msg# (exception/stringify e#)
             swap-fun# (fn [curr#]
                         (conj curr# {:state "failed", 
                                      :error error-msg#
                                      :errors (conj (or (:errors curr#) [])
                                                    error-msg#)}))]
         (clj-logging/error error-msg#)
         (swap! ~state-container swap-fun#)))
     (finally ~state-container)))


;(catch-and-fail-state (atom {}) (throw (IllegalStateException. "Blah")))
;(macroexpand-1 '(log-debug-result(+ 1 2)))

