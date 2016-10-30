; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.status-pushes.repository-updates
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.status-pushes.remotes :refer [dispatch]]
    [cider-ci.repository.status-pushes.shared :refer [base-query]]

    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.row-events :as row-events]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn push-recent-statuses-for-repository [repository-id]
  (->> (-> base-query
           (sql/merge-join :branches [:= :branches.repository_id :repositories.id])
           (sql/merge-join :commits [:= :commits.id :branches.current_commit_id])
           (sql/merge-join :jobs [:= :jobs.tree_id :commits.tree_id])
           (sql/merge-where [:= :repositories.id repository-id])
           (sql/merge-where (sql/raw (str "( jobs.updated_at  > now() - (interval '24 Hours'))")))
           sql/format)
       (jdbc/query (rdbms/get-ds))
       dispatch))

(defn push-recent-statuses-for-all-repositories []
  (doseq [repository-id (->> ["SELECT id FROM repositories"]
                             (jdbc/query (rdbms/get-ds))
                             (map :id))]
    (push-recent-statuses-for-repository repository-id)))



(defn evaluate-repository-event [repository-id]
  (push-recent-statuses-for-repository repository-id))

(def last-processed-repository-event (atom nil))

(defdaemon "process-repository-events" 1
  (row-events/process "repository_events" last-processed-repository-event
                      (fn [row] (evaluate-repository-event (:repository_id row)))))


(defn initialize []
  (push-recent-statuses-for-all-repositories)
  (start-process-repository-events))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
