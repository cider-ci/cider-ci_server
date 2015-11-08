; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.tasks
  (:require
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.task :as task]
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.map :as map :refer [deep-merge]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as  logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [drtom.logbug.thrown]
    [langohr.basic     :as lb]
    [langohr.channel   :as lch]
    [langohr.consumers :as lc]
    [langohr.core      :as rmq]
    [langohr.exchange  :as le]
    [langohr.queue     :as lq]
    ))


;### utils ####################################################################
(defmacro wrap-exception-create-job-issue [job title & body]
  `(try
     ~@body
     (catch Exception e#
       (let [row-data#  {:job_id (:id ~job)
                         :title ~title
                         :description (str (.getMessage e#) "\n\n"  (thrown/stringify e#))}]
         (logging/warn ~job row-data# e#)
         (jdbc/insert! (rdbms/get-ds) "job_issues" row-data#)))))

(defn get-job [id]
  (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM jobs WHERE id =?" id ])))


;### build tasks ##############################################################

(defn- assert-scripts-is-a-map! [scripts]
  (if (map? scripts)
    scripts
    (throw (IllegalStateException. (str ":scripts must a map " scripts)))))

(defn- coerce-script-value [value]
  (cond
    (map? value) value
    (string? value) {:body value}
    :else (throw (IllegalStateException.
                   (str "don't know how to handle type " (type value))))))

(defn- build-base-script [[script-key value]]
  [script-key (coerce-script-value value)])

;(build-base-script (into [] {:x "test a = a"}))

(defn build-scripts [task script-defaults]
  (->> task
       :scripts
       (#(or % {}))
       (map build-base-script)
       (map (fn [[k v]] [k (deep-merge script-defaults v)]))
       (into {})))

;(build-scripts {:scripts [{:x 5}]} {:x 7 :y 9})
;(build-scripts {:scripts {:blah {:x 5}}} {:x 7 :y 9})

(defn build-task [task-spec task-defaults script-defaults]
  (let [merged-task (deep-merge task-defaults task-spec)]
    (assoc merged-task :scripts (build-scripts merged-task script-defaults))))

; build-tasks-for-single-context and build-tasks-for-contexts-sequence
; call each other recursively; no need for trampoline, sensible specs
; should not blow the stack
(declare build-tasks-for-contexts-sequence)
(defn build-tasks-for-single-context [context task-defaults script-defaults]
  "Build the tasks for a single context."
  (concat (->> (:tasks context)
               convert-to-array
               (map (fn [task-spec] (build-task task-spec
                                                task-defaults
                                                script-defaults))))
          (if-let [subcontexts (:subcontexts context)]
            (build-tasks-for-contexts-sequence
               (convert-to-array subcontexts) task-defaults script-defaults)
            [])))

(defn build-tasks-for-contexts-sequence
  "Build the tasks for a sequence of contexts."
  [context inherited-task-defaults inherited-script-defaults]
  (apply concat
         (map
           (fn [context]
             (logging/debug {:context context})
             (let [task-defaults (deep-merge inherited-task-defaults
                                                  (or (:task-defaults context)
                                                      {}))
                   script-defaults (deep-merge inherited-script-defaults
                                                    (or (:script-defaults context)
                                                        {}))]
               (build-tasks-for-single-context context
                                               task-defaults
                                               script-defaults)))
           context)))

(def ^:private script-base-defaults {})

(defn build-tasks
  "Build the tasks for the given top-level specification."
  [job _spec]
  (let [spec (clojure.walk/keywordize-keys _spec)
        tasks (build-tasks-for-single-context
                spec
                (conj (or (:task-defaults spec) {})
                      {:job_id (:id job)})
                (deep-merge script-base-defaults (or (:script-defaults spec) {})))]
    (doseq [raw-task tasks]
      (wrap-exception-create-job-issue
        job "Error during task creation"
        (task/create-db-task raw-task)))))


;### create tasks for job ###############################################

(defn create-tasks [job]
  (wrap-exception-create-job-issue
    job "Error when creating tasks"
    (let [spec (-> (jdbc/query (rdbms/get-ds)
                               ["SELECT * FROM job_specifications WHERE id = ?"
                                (:job_specification_id job)])
                   first
                   :data
                   :context)]
      (build-tasks job spec))))

(defn- assert-tasks [job]
  (when (= 0 (-> (jdbc/query (rdbms/get-ds)
                             ["SELECT count(*) AS count FROM tasks WHERE job_id = ?"
                              (:id job)])
                 first :count))
    (jdbc/update! (rdbms/get-ds) :jobs
                  {:state "failed"}
                  ["id = ? " (:id job)])
    (throw (IllegalStateException.
             "This job failed because no tasks have been created for it."))))

(defn create-tasks-and-trials [message]
  (if-let [job (get-job (:job_id message))]
    (wrap-exception-create-job-issue
      job "Error during create-tasks-and-trials"
      (-> job create-tasks)
      (doseq [task-with-id (jdbc/query
                             (rdbms/get-ds)
                             ["SELECT id FROM tasks WHERE job_id = ?"
                              (:id job)])]
        (messaging/publish "task.create-trials" task-with-id))
      (assert-tasks job))
    (throw (IllegalStateException. (str "could not find job for " message)))))

;### initialization ###########################################################
(defn initialize []
  (logging/debug "initialize")
  (catcher/wrap-with-log-warn
    (messaging/listen "job.create-tasks-and-trials"
                      #'create-tasks-and-trials
                      "job.create-tasks-and-trials")))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
