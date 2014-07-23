; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.system
  (:require 
    [clj-commons-exec :as commons-exec]
    [clojure.tools.logging :as logging]
    ))


(defn exec 
  ([command]
   (exec command {}))
  ([command opts]
   (let [options (conj {:watchdog 1000} opts)
         res @(commons-exec/sh command options)]
     (logging/debug exec {:res res})

     (if (not= 0 (:exit res))
       (throw (IllegalStateException. (str "Unsuccessful shell execution" 
                                           command
                                           options
                                           {:result res}))) 
       res)
     )))


;(exec ["ls" "-lah"]{:dir "/tmp"})
;(exec ["sleep" "1"])
