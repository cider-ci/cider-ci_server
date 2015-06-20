; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.task
  (:require
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.builder.spec :as spec]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.builder.util :as util]
  ))


(defn spec-map-to-array [spec-map]
  (map name
       (filter (complement nil?)
               (for [[k v] spec-map] (when v k)))))

(defn create-db-task [_raw-spec]
  (let [raw-spec (clojure.walk/keywordize-keys _raw-spec)
        db-task-spec (spec/get-or-create-task-spec (dissoc raw-spec
                                                           :job_id))
        job-id (:job_id raw-spec)
        task-row (conj (select-keys raw-spec [:name :state :error :priority])
                       {:job_id job-id
                        :traits (spec-map-to-array (or (:traits raw-spec) {}))
                        :exclusive_global_resources (spec-map-to-array (or (:exclusive-global-resources raw-spec) {}))
                        :task_specification_id (:id db-task-spec)
                        :id (util/idid2id job-id (:id db-task-spec))
                        })]
    (logging/debug task-row)
    (first (jdbc/insert! (rdbms/get-ds) "tasks" task-row))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
