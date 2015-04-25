; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.system
  (:require 
    [drtom.logbug.debug :as debug]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))


(defn exec 
  ([command]
   (exec command {}))
  ([command opts]
   (let [options (conj {:watchdog 1000} 
                       opts)]
     @(commons-exec/sh command options))))
     

(defn exec-with-success-or-throw [& args]
  (let [res (apply exec args)]
    (if (= 0 (:exit res))
      res
      (throw (IllegalStateException. 
               (str "Unsuccessful shell execution" 
                    [args]
                    {:result res}))))))


(defn exec-with-success? [& args]
  (= 0 (:exit (apply exec args)))) 


;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
