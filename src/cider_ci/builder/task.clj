; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.task
  (:require
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.util :as util]

    [cider-ci.utils.config :as config]
    [cider-ci.utils.duration :as duration]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
  ))


(defn spec-map-to-array [spec-map]
  (map name
       (filter (complement nil?)
               (for [[k v] spec-map] (when v k)))))


;##############################################################################

(defn- check-aggregate-state [spec]
  (let [value (:aggregate_state spec)]
    (case value
      ("satisfy-last" "satisfy-any") []
      [(str "### Validation Error\n "
            "The value of `aggregate_state` must be either"
            "`satisfy-any` for `satisfy-last` but it is: `" value "`.")])))

(defn- validated-task-spec [spec]
  (loop [errors []
         pending-checks [check-aggregate-state]]
    (if-let [next-check (first pending-checks)]
      (recur (concat errors (apply next-check [spec]))
             (rest pending-checks))
      errors)))


;### normalize task-spec ######################################################

(defn- normalize-aggregate-succes [spec]
  (if (:aggregate_state spec)
    spec
    (assoc spec :aggregate_state "satisfy-any")))

(defn dispatch-storm-delay-default []
  (-> (or (config/parse-config-duration-to-seconds
            :dispatch_storm_delay_default_duration)
          1)
      int))

(defn- normalize-dispatch-storm-delay [spec]
  (if-let [dispatch-storm-delay-duration (:dispatch_storm_delay_duration spec)]
    (-> spec (dissoc :dispatch_storm_delay_duration)
        (assoc :dispatch_storm_delay_seconds
               (-> dispatch-storm-delay-duration
                   duration/parse-string-to-seconds
                   Math/floor int)))
    (assoc spec :dispatch_storm_delay_seconds (dispatch-storm-delay-default))))


(defn- normalize-template-environment-variables [spec]
  (if-let [scripts (:scripts spec)]
    (assoc spec :scripts
           (->> scripts
                (into [])
                (map (fn [[k script]]
                       [k (if (contains? script :template_environment_variables )
                            script
                            (assoc script :template_environment_variables true))]))
                (into {})))
    spec))

(defn- normalize-task-spec [raw-spec]
  (-> raw-spec
      clojure.walk/keywordize-keys
      (dissoc :job_id)
      normalize-aggregate-succes
      normalize-dispatch-storm-delay
      normalize-template-environment-variables))


;##############################################################################

(defn create-db-task [tx raw-task-spec]
  (let [job-id (:job_id raw-task-spec)
        task-spec (normalize-task-spec raw-task-spec)
        db-task-spec (spec/get-or-create-task-spec task-spec)
        errors (validated-task-spec task-spec)
        task-row (conj (select-keys task-spec [:name :state :error :priority :load
                                               :dispatch_storm_delay_seconds])
                       {:job_id job-id
                        :traits (spec-map-to-array (or (:traits task-spec) {}))
                        :entity_errors errors
                        :exclusive_global_resources (-> (or (:exclusive_global_resources
                                                              task-spec) {})
                                                        spec-map-to-array)
                        :task_specification_id (:id db-task-spec)
                        :state (if (empty? errors) "pending" "aborted")
                        :id (util/idid2id job-id (:id db-task-spec))
                        })]
    (first (jdbc/insert! tx "tasks" task-row))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
