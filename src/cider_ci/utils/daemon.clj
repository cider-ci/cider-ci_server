; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.daemon
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    ))



(defmacro defdaemon [daemon-name secs-pause & body]
  (let [stop (gensym "_stop_")
        start-fn (symbol (str "start-" daemon-name))
        stop-fn (symbol (str "stop-" daemon-name))]
    `(do

       (defonce ~stop (atom (fn [])))

       (defn ~stop-fn []
         (@~stop))

       (defn ~start-fn []
         (~stop-fn)
         (let [done# (atom false)
               runner# (future (logging/info "daemon " ~daemon-name " started")
                               (loop []
                                 (when-not @done#
                                   (catcher/wrap-with-suppress-and-log-error
                                     ~@body)
                                   (Thread/sleep (Math/ceil (* ~secs-pause 1000)))
                                   (recur))))]
           (reset! ~stop (fn []
                           (reset! done# true)
                           (future-cancel runner#)
                           ;@runner#
                           (logging/info "daemon " ~daemon-name "stopped")))

           (.addShutdownHook (Runtime/getRuntime)
                             (Thread. (fn [] (~stop-fn))))

           )))))

;(macroexpand-1 '(defdeamon "blah" 10 (logging/info "looping ...")))


(defmacro define
  {:deprecated "3.2"}
  [daemon-name start-fn stop-fn secs-pause & body]
  (logging/warn "daemon/define is deprecated use defdaemon")
  (let [stop (gensym "_stop_")]
    `(do

       (defonce ~stop (atom (fn [])))

       (defn ~stop-fn []
         (@~stop))

       (defn ~start-fn []
         (~stop-fn)
         (let [done# (atom false)
               runner# (future (logging/info "daemon " ~daemon-name " started")
                               (loop []
                                 (when-not @done#
                                   (catcher/wrap-with-suppress-and-log-error
                                     ~@body)
                                   (Thread/sleep (Math/ceil (* ~secs-pause 1000)))
                                   (recur))))]
           (reset! ~stop (fn []
                           (reset! done# true)
                           (future-cancel runner#)
                           ;@runner#
                           (logging/info "daemon " ~daemon-name "stopped")))

           (.addShutdownHook (Runtime/getRuntime)
                             (Thread. (fn [] (~stop-fn))))

           )))))


;(macroexpand-1 '(define "blah"  10 (logging/info "looping ...")))

