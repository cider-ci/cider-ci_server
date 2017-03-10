; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.triggers
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require

    [cider-ci.builder.jobs.triggers.tree-ids.branch-update-daemon :as branch-update-daemon]
    [cider-ci.builder.jobs.triggers.tree-ids.job-update-daemon :as job-update-daemon]
    [cider-ci.builder.jobs.triggers.cron.daemon :as cron-daemon]

    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]

    ))


;### initialize ###############################################################

(defn initialize []
  (branch-update-daemon/initialize)
  (job-update-daemon/initialize)
  (cron-daemon/initialize))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'trigger-jobs)
;(debug/debug-ns *ns*)
