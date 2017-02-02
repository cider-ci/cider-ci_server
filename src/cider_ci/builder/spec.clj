;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.spec
  (:require
    [cider-ci.builder.util :as util]
    [logbug.debug :as debug]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-uuid]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    ))

(defn- get-or-create-spec [data table-name]
  (let [id (util/id-hash data)]
    (or (first (jdbc/query (rdbms/get-ds)
                           [(str "SELECT * FROM " table-name
                                 " WHERE id = ?") id]))
        (first (jdbc/insert! (rdbms/get-ds) table-name
                             {:id id :data data})))))

(defn get-or-create-job-spec [data]
  (get-or-create-spec data "job_specifications"))

(defn get-or-create-task-spec [data]
  (get-or-create-spec data "task_specifications"))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
