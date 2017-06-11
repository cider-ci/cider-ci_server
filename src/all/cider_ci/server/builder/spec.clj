;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.spec
  (:require
    [cider-ci.server.builder.util :as util]
    [logbug.debug :as debug]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-uuid]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    ))

(defn- get-or-create-spec
  ([data table-name tx]
   (let [id (util/id-hash data)]
     (or (first (jdbc/query tx
                            [(str "SELECT * FROM " table-name
                                  " WHERE id = ?") id]))
         (first (jdbc/insert! tx table-name
                              {:id id :data data}))))))

(defn get-or-create-job-spec
  ([data] (get-or-create-job-spec data (rdbms/get-ds)))
  ([data tx] (get-or-create-spec data "job_specifications" tx)))

(defn get-or-create-task-spec
  ([data] (get-or-create-task-spec data (rdbms/get-ds)))
  ([data tx] (get-or-create-spec data "task_specifications" tx)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
