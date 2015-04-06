; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.tasks
  (:require 
    [cider-ci.builder.expansion :as expansion]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.task :as task]
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as  logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
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
                         :description (str (.getMessage e#) "\n\n"  (exception/stringify e# "\\n"))}]
         (logging/warn ~job row-data# e#)
         (jdbc/insert! (rdbms/get-ds) "job_issues" row-data#)))))

(defn get-job [id]
  (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM jobs WHERE id =?" id ])))


;### build tasks ##############################################################

(defn build-scripts [task script-defaults]
  (into {} (for [[name-key script] (or (:scripts task) {})]
             (let [final-script  (util/deep-merge script-defaults script)]
               [name-key final-script]))))

(defn build-task [task-spec task-defaults script-defaults name-prefix]
  (let [merged-task (util/deep-merge task-defaults task-spec)]
    ;(logging/debug {:merged-task merged-task})
    (conj merged-task
          {:scripts (build-scripts merged-task script-defaults)
           :name (str name-prefix " » " (:name merged-task))
           })))

(defn value-seq-for-map-or-array 
  [mapar]
  "Accepts a map or any other collection and returns a seq of the values in the
  first case and the collection itself in the second. Returns an empty array in
  any other case." 
  (cond 
    (map? mapar) (map second mapar)
    (coll? mapar) mapar  ; throw this if we do not accept arrays anymore
    :else [] ))

; build-tasks-for-single-context and build-tasks-for-contexts-sequence 
; call each other recursively; no need for trampoline, sensible specs
; should not blow the stack
(declare build-tasks-for-contexts-sequence)
(defn build-tasks-for-single-context [context task-defaults script-defaults name-prefix]
  "Build the tasks for a single context."
  (let [new-name-prefix (str name-prefix (if (empty? name-prefix) "" " » ") (:name context))]
    (concat (map (fn [task-spec]
                   (build-task task-spec 
                               task-defaults 
                               script-defaults
                               new-name-prefix))
                 (value-seq-for-map-or-array (:tasks context)))
            (if-let [subcontexts-spec (:subcontexts context)]
              (build-tasks-for-contexts-sequence (value-seq-for-map-or-array subcontexts-spec)
                                                 task-defaults 
                                                 script-defaults
                                                 new-name-prefix) 
              []))))

(defn build-tasks-for-contexts-sequence
  "Build the tasks for a sequence of contexts."
  [context-spec inherited-task-defaults inherited-script-defaults name-prefix]
  (apply concat 
         (map 
           (fn [context]
             (logging/debug {:context context})
             (let [task-defaults (util/deep-merge inherited-task-defaults
                                             (or (:task_defaults context) 
                                                 {}))
                   script-defaults (util/deep-merge inherited-script-defaults
                                               (or (:script_defaults context) 
                                                   {}))]
               (build-tasks-for-single-context context 
                                               task-defaults 
                                               script-defaults
                                               name-prefix)))
           context-spec)))

(defn build-tasks 
  "Build the tasks for the given top-level specification."
  [job _spec]
  (let [spec (clojure.walk/keywordize-keys _spec)
        tasks (build-tasks-for-single-context 
                spec
                (conj (or (:task_defaults spec) {})
                      {:job_id (:id job)})
                (or (:script_defaults spec) {})
                "")] 
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
                   first :data)]
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
  (with/log :warn
    (messaging/listen "job.create-tasks-and-trials" 
                      #'create-tasks-and-trials 
                      "job.create-tasks-and-trials")))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
