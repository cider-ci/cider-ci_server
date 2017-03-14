; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.triggers.cron.daemon
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.jobs.triggers.cron.core :as cron.core]
    [cider-ci.utils.daemon :refer [defdaemon]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defdaemon "cron-branch-trigger" 30 (cron.core/cron-branch-trigger))

(defn initialize []
  (start-cron-branch-trigger))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
