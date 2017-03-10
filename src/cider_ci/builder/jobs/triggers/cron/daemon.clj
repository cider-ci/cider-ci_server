; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.triggers.cron.daemon
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.jobs.triggers.shared :as shared]
    [cider-ci.shared.cron]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.map :refer [convert-to-array]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(def query
  (-> (sql/select :tree_id
                  [:branches.name :branch_name]
                  [:branches.updated_at :branch_updated_at]
                  [:repositories.git_url :repository_git_url]
                  [:repositories.name :repository_name])
      (sql/modifiers :distinct)
      (sql/from :commits)
      (sql/merge-join :branches [:= :branches.current_commit_id :commits.id])
      (sql/merge-join :repositories [:= :repositories.id :branches.repository_id])
      (sql/merge-where [:= :repositories.cron_trigger_enabled true])
      sql/format))

;##############################################################################


(defn- cron-run-when-fulfilled? [cron-run-when]
  (cider-ci.shared.cron/fire? (:value cron-run-when) 5))


(defn- some-cron-run-when-fulfilled? [event job-config]
  (->> job-config :run_when convert-to-array
       (filter (fn [rw] (= (-> rw :type keyword) :cron)))
       (some cron-run-when-fulfilled?)
       boolean))

(defn triggered-jobs [event]
  (->> (shared/filtered-run-when-event-type-jobs (:tree_id event) "cron")
       (filter #(some-cron-run-when-fulfilled? event %))
       seq))

;##############################################################################

(defn- create-jobs-for-cron-event [event]
  (->> event triggered-jobs shared/create-jobs))

;##############################################################################



(defn cron-branch-trigger []
  (debug/I>> debug/identity-with-logging
             ;(->>
             query
             (jdbc/query (rdbms/get-ds))
             ;(map #(catcher/snatch {} (trigger-jobs %)))
             (map create-jobs-for-cron-event)
             doall))

;(cron-branch-trigger)

(defdaemon "cron-branch-trigger" 30 (cron-branch-trigger))

(defn initialize []
  (start-cron-branch-trigger))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
