; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.jobs.triggers.shared
  (:refer-clojure :exclude [str keyword])

  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.jobs.dependencies :as jobs.dependencies]
    [cider-ci.server.builder.jobs :as jobs]
    [cider-ci.server.builder.project-configuration :as project-configuration]
    [cider-ci.server.builder.issues :refer [create-issue]]

    [cider-ci.utils.map :refer [convert-to-array]]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.core.memoize :as memo]

    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))


;##############################################################################

(defn- filtered-run-when-event-type-jobs_unmemoized [tree-id event-type]
  (catcher/snatch
    {:return-fn (fn [e]
                  (create-issue "tree" tree-id e)
                  [])}
    (->> (project-configuration/get-project-configuration tree-id)
         :jobs convert-to-array
         (filter (fn [job]
                   (some (fn [run-when-item]
                           (= (keyword event-type)
                              (-> run-when-item :type keyword)))
                         (-> job :run_when convert-to-array))))
         (map #(assoc % :tree_id tree-id)))))

(def filtered-run-when-event-type-jobs
  (memo/lru filtered-run-when-event-type-jobs_unmemoized
            :lru/threshold 1000))

;##############################################################################


(defn job-does-not-exists-yet? [job-config]
  (->> [(str "SELECT true AS exists_yet FROM jobs "
             "WHERE tree_id = ? AND key = ? ")
        (:tree_id job-config) (:key job-config)]
       (jdbc/query (rdbms/get-ds))
       first :exists_yet not))

(defn create-jobs [jobs]
  (doseq [job jobs]
    (locking (-> job :tree_id str)
      (when (and (jobs.dependencies/fulfilled? job)
                 (job-does-not-exists-yet? job)
                 (jobs/create job))))))
