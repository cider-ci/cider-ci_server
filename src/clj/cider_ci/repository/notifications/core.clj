; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.notifications.core
  (:require
    [cider-ci.repository.notifications.branch-updates]
    [cider-ci.repository.notifications.job-updates]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))


(defn initialize []
  (cider-ci.repository.notifications.branch-updates/initialize)
  (cider-ci.repository.notifications.job-updates/initialize))
