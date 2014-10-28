;; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.spec
  (:require 
    [cider-ci.builder.util :as util]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-uuid]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    ))

(defn get-execution-spec [id]
  (-> (jdbc/query 
        (rdbms/get-ds) 
        [(str " SELECT * FROM specifications"
              " WHERE id = ?::UUID") id])
      first)
  ;(get-execution-spec "caf72b4c-26bf-5893-9f08-ce72f4af8605")
  )

(defn get-or-create-execution-specification [data]
  (let [id (util/id-hash data)]
    (or (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM specifications WHERE id = ?" id]))
        (first(jdbc/insert! (rdbms/get-ds) :specifications {:id id :data data})))))

(defn get-or-create-task-spec [data]
  (let [id (util/id-hash data)]
    (or (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM task_specs WHERE id = ?" id]))
        (first(jdbc/insert! (rdbms/get-ds) :task_specs {:id id :data data})))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
