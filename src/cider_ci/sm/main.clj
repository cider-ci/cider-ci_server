; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.main
  (:require 
    [clojure.tools.logging :as logging]
    [drtom.config-loader :as config-loader]
    ))


(defonce conf (atom {}))
(defonce rdbms-ds (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    [conf]
    ["/etc/cider-ci/storage-manager/conf" "conf"]))

(defn -main [& args]
  (logging/debug [-main args]))
