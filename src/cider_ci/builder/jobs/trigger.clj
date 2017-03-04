; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.issues :refer [create-issue]]
    [cider-ci.builder.jobs :as jobs]
    [cider-ci.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.builder.jobs.trigger.cron :as trigger.cron]
    [cider-ci.builder.jobs.trigger.branches :as trigger.branches]
    [cider-ci.builder.jobs.trigger.jobs :as trigger.jobs]
    [cider-ci.builder.jobs.trigger.recurring-branch-trigger-daemon :as recurring-branch-trigger-daemon]
    [cider-ci.builder.jobs.trigger.tree-id-notification-daemon :as tree-id-notification-daemon]
    [cider-ci.builder.project-configuration :as project-configuration]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]

    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.jdbc :refer [insert-or-update]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))




;##############################################################################

(defn trigger-fulfilled? [tree-id job trigger]
  (case (:type trigger)
    "job" (trigger.jobs/job-trigger-fulfilled? tree-id job trigger)
    "branch" (trigger.branches/branch-trigger-fulfilled? tree-id job trigger)
    "cron" (trigger.cron/fulfilled? tree-id job trigger)
    (do (logging/warn "unhandled run_when" trigger) false)))

(defn some-trigger-fulfilled? [tree-id job]
  (when-let [triggers (:run_when job)]
    (some (fn [[_ trigger]]
            (trigger-fulfilled? tree-id job trigger)) triggers)))

(defn job-does-not-exist-yet [job-config]
  (->> [(str "SELECT true AS exists_yet FROM jobs "
             "WHERE tree_id = ? AND key = ? ")
        (:tree_id job-config) (:key job-config)]
       (jdbc/query (rdbms/get-ds))
       first :exists_yet not))


;##############################################################################

(defn- trigger-jobs [tree-id]
  (locking (str tree-id)
    (catcher/snatch
      {:return-fn (fn [e] (create-issue "tree" tree-id e))}
      (->> ;identity-with-logging
           (project-configuration/get-project-configuration tree-id)
           :jobs
           convert-to-array
           (filter #(-> % :run_when))
           (filter #(some-trigger-fulfilled? tree-id %))
           (map #(assoc % :tree_id tree-id))
           (filter jobs.dependencies/fulfilled?)
           (filter job-does-not-exist-yet)
           (map jobs/create)
           doall)
      (jdbc/delete! (rdbms/get-ds) :tree_issues ["tree_id = ?" tree-id])))
  tree-id)


;### initialize ###############################################################

(defn initialize []
  (recurring-branch-trigger-daemon/initialize #'trigger-jobs)
  (tree-id-notification-daemon/initialize #'trigger-jobs)
  )


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
