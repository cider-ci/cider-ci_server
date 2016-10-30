; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.status-pushes.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.status-pushes.remotes.github :as github]
    [cider-ci.repository.status-pushes.branch-updates :as branch-updates]
    [cider-ci.repository.status-pushes.job-updates :as job-updates]
    [cider-ci.repository.status-pushes.repository-updates :as repository-updates]
    [cider-ci.repository.status-pushes.shared :refer [base-query]]
    [cider-ci.repository.status-pushes.remotes :refer [dispatch]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))



(def push-recent-statuses-for-repository
  repository-updates/push-recent-statuses-for-repository)

;### Initialize ###############################################################

(defn initialize []
  (repository-updates/initialize)
  (branch-updates/initialize)
  (job-updates/initialize))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
