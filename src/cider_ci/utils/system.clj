; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.system
  (:require 
    [clj-commons-exec :as commons-exec]
    [clojure.tools.logging :as logging]
    ))


(defn exec [& args]
  (logging/debug exec [args])
  (let [res @(apply commons-exec/sh args)]
    (if (not= 0 (:exit res))
      (throw (IllegalStateException. (str "Unsuccessful shell execution" 
                                          args
                                          (:err res)
                                          (:out res)))) 
      res)))

