; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch
  (:require
    [cider-ci.dispatcher.dispatch.build-data :as build-data]
    [cider-ci.dispatcher.dispatch.next-trial :as next-trial]
    [cider-ci.dispatcher.executor :as executor-utils]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clj-time.core :as time]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)

