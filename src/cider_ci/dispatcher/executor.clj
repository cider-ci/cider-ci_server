; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.executor
  (:require
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.config :as config]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [pandect.algo.sha1 :refer [sha1-hmac]]
    ))


(defn http-basic-password [executor]
  (sha1-hmac (:name executor) (:secret (config/get-config))))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

