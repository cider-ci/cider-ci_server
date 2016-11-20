; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger
  (:require
    [cider-ci.builder.jobs :as jobs]
    [cider-ci.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.builder.project-configuration :as project-configuration]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.issues :refer [create-issue]]
    [cider-ci.builder.jobs.trigger.branches :as trigger.branches]
    [cider-ci.builder.jobs.trigger.jobs :as trigger.jobs]

    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.daemon :refer [defdaemon]]
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
    (do (logging/warn "unhandled run_when" trigger) false)))

(defn some-trigger-fulfilled? [tree-id job]
  (when-let [triggers (:run_when job)]
    (some (fn [[_ trigger]]
            (trigger-fulfilled? tree-id job trigger)) triggers)))


;##############################################################################

(defn- trigger-jobs [tree-id]
  (locking tree-id
    (catcher/snatch
      {:return-fn (fn [e] (create-issue "tree" tree-id e))}
      (I>> identity-with-logging
           (project-configuration/get-project-configuration tree-id)
           :jobs
           convert-to-array
           (filter #(-> % :run_when))
           (filter #(some-trigger-fulfilled? tree-id %))
           (map #(assoc % :tree_id tree-id))
           (filter jobs.dependencies/fulfilled?)
           (map jobs/create)
           doall)))
  tree-id)


;##############################################################################

(defn evaluate-tree-id-notifications []
  (I>> identity-with-logging
       "SELECT * FROM tree_id_notifications ORDER BY created_at ASC LIMIT 100"
       (jdbc/query (rdbms/get-ds))
       (map (fn [row]
              (future (trigger-jobs (:tree_id row))
                      row)))
       (map deref)
       (map :id)
       (map #(jdbc/delete! (rdbms/get-ds) :tree_id_notifications ["id = ?" %]))
       doall))

(defdaemon "evaluate-tree-id-notifications" 1 (evaluate-tree-id-notifications))


;### initialize ###############################################################

(defn initialize []
  (start-evaluate-tree-id-notifications))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
