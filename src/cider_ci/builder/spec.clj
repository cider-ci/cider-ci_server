;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.spec
  (:require 
    [cider-ci.builder.util :as util]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-uuid]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    ))

(defn get-job-specification [id]
  (-> (jdbc/query 
        (rdbms/get-ds) 
        [(str " SELECT * FROM job_specifications"
              " WHERE id = ?") id])
      first)
  ;(get-job-specification "caf72b4c-26bf-5893-9f08-ce72f4af8605")
  )

(defn get-or-create-job-specification [data]
  (let [id (util/id-hash data)]
    (or (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM job_specifications WHERE id = ?" id]))
        (first(jdbc/insert! (rdbms/get-ds) :job_specifications {:id id :data data})))))

(defn get-or-create-task-spec [data]
  (let [id (util/id-hash data)]
    (or (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM task_specifications WHERE id = ?" id]))
        (first(jdbc/insert! (rdbms/get-ds) :task_specifications {:id id :data data})))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

