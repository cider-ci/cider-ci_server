; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger.tree-id-notification-daemon
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    ))

(declare trigger-jobs)

(defn evaluate-tree-id-notifications []
  (->> ;identity-with-logging
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

(defn initialize [trigger-jobs]
  (def trigger-jobs trigger-jobs)
  (start-evaluate-tree-id-notifications))
