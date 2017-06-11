; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.retention-sweeper.jobs
  (:require
    [cider-ci.utils.config :refer [get-config parse-config-duration-to-seconds]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.server.builder.retention-sweeper.shared :refer [retention-interval]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn- job-overdue-query-part [job-retention-interval]
  (str "(jobs.updated_at < (now() - interval '" job-retention-interval "'))"))

(def ^:private job-not-directly-releated-to-branch-query-part
  [:not [:exists
         (-> (sql/select 1)
             (sql/from :commits)
             (sql/where [:= :jobs.tree_id :commits.tree_id])
             (sql/merge-join :branches [:= :branches.current_commit_id
                                        :commits.id]))]])

(defn- to-be-swept-jobs-query []
  (when-let [job-retention-interval (retention-interval :job_retention_duration)]
    (-> (-> (sql/select :jobs.id)
            (sql/from :jobs)
            (sql/merge-where (sql/raw (job-overdue-query-part
                                        job-retention-interval)))
            (sql/merge-where job-not-directly-releated-to-branch-query-part)
            )sql/format)))

(defn- delete-jobs [job-ids]
  (->> (-> (sql/delete-from :jobs)
           (sql/merge-where [:in :jobs.id job-ids])
           (sql/returning :jobs.name :jobs.id :jobs.tree_id :jobs.key)
           sql/format)
       (jdbc/query (get-ds))))

(defn- sweep-jobs []
  (catcher/snatch {}
    (when-let [ids-query (to-be-swept-jobs-query)]
      (when-let [job-ids (->> ids-query
                              (jdbc/query (get-ds))
                              (map :id)
                              seq)]
        (delete-jobs job-ids)))))

(defdaemon "sweep-jobs" 1 (sweep-jobs))

(defn initialize []
  (start-sweep-jobs))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
