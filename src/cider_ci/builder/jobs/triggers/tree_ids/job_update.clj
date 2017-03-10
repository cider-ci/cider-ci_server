; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.triggers.tree-ids.job-update
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])

  (:require
    [cider-ci.builder.jobs.triggers.shared :as shared]

    [cider-ci.builder.jobs :as jobs]
    [cider-ci.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.builder.jobs.triggers.tree-ids.branch-update :as branch-update]

    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.rdbms :as rdbms]

    [honeysql.core :as sql]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))

;##############################################################################


(defn- base-query [ids]
  (-> (sql/select
        (sql/raw " 'branch' AS type ")
        [:jobs.state :job_state]
        [:jobs.key :job_key]
        [:jobs.name :job_name]
        [:tree_id_notifications.id :id]
        [:tree_id_notifications.tree_id :tree_id])
      (sql/from :tree_id_notifications)
      (sql/merge-join :jobs [:= :jobs.id :tree_id_notifications.job_id])
      (sql/merge-where [:in :tree_id_notifications.id ids])
      sql/format))

;##############################################################################

(defn- job-run-when-fulfilled? [event run-when]
  (and (= (-> run-when :job_key)
          (-> event :job_key))
       (= (-> run-when :type)
          "job")
       (some (-> run-when :states set)
             [(:job_state event)])))

(defn- some-job-run-when-fulfilled? [event job-config]
  (->> job-config :run_when convert-to-array
       (filter (fn [rw] (= (-> rw :type keyword) :job)))
       (some #(job-run-when-fulfilled? event %))
       boolean))

(defn triggered-jobs [event]
  (->> (shared/filtered-run-when-event-type-jobs (:tree_id event) "job")
       (filter #(some-job-run-when-fulfilled? event %))
       seq))

;##############################################################################

(defn- create-jobs-for-job-update [event]
  (->> event triggered-jobs shared/create-jobs))

;##############################################################################

(defn build-and-trigger [tx tree-id-notification-ids]
  (debug/I>> debug/identity-with-logging
             ; (->>
             (base-query tree-id-notification-ids)
             (jdbc/query tx)
             (map #(update-in % [:type] keyword))
             (map create-jobs-for-job-update)
             doall))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

