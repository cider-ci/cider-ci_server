; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.sync
  (:refer-clojure :exclude [sync])
  (:require
    [cider-ci.dispatcher.executor :as executor-entity]
    [cider-ci.dispatcher.sync.ping :as ping]
    [cider-ci.dispatcher.sync.sync-trials :as sync-trials]
    [cider-ci.dispatcher.sync.update-executor :as update-executor]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [clojure.data.json :as json]
    ))

(defn sync [executor data]
  (catcher/wrap-with-log-warn
    (logging/info 'sync executor data)
    (update-executor/update-when-changed executor data)
    (ping/update-last-ping-at executor)
    ;jobs-to-execute (get-jobs-to-execute executor (:body request))]
    {:status 200
     :body (json/write-str (sync-trials/sync-trials executor data))}))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)

