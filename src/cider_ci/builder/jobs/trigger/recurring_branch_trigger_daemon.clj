; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger.recurring-branch-trigger-daemon
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [logbug.catcher :as catcher]
    ))

(declare trigger-jobs)

(defn recurring-branch-trigger []
  (->> (-> (sql/select :tree_id)
           (sql/modifiers :distinct)
           (sql/from :commits)
           (sql/merge-join :branches [:= :branches.current_commit_id
                                      :commits.id])
           (sql/merge-where
             (sql/raw "(branches.updated_at > (now() - interval '7 days'))"))
           sql/format)
       (jdbc/query (rdbms/get-ds))
       (map :tree_id)
       (map #(catcher/snatch {} (trigger-jobs %)))
       doall))

(defdaemon "recurring-branch-trigger" 30 (recurring-branch-trigger))

(defn initialize [trigger-jobs]
  (def trigger-jobs trigger-jobs)
  (start-recurring-branch-trigger))


