; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger.jobs
  (:require
    [cider-ci.utils.include-exclude :as include-exclude]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))

(defn job-trigger-fulfilled? [tree-id job trigger]
  (logging/debug 'job-trigger-fulfilled? [tree-id job trigger])
  (let [query (-> (-> (sql/select true)
                      (sql/from :jobs)
                      (sql/merge-where [:= :tree_id tree-id])
                      (sql/merge-where [:= :key (:job_key trigger)])
                      (sql/merge-where [:in :state (or (:states trigger) ["passed"])])
                      (sql/limit 1)) sql/format) ]
    (logging/debug query)
    (->> query
         (jdbc/query (rdbms/get-ds))
         first
         boolean)))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
