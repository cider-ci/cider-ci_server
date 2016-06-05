; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.tasks
  (:require
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.task :as task]
    [cider-ci.builder.util :as util]

    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [langohr.basic     :as lb]
    [langohr.channel   :as lch]
    [langohr.consumers :as lc]
    [langohr.core      :as rmq]
    [langohr.exchange  :as le]
    [langohr.queue     :as lq]

    [clj-logging-config.log4j :as  logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
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
  (->> ["SELECT * FROM jobs WHERE id =?" id ]
  (jdbc/query (rdbms/get-ds))
  first))

(defn get-job-spec [job]
  (->> ["SELECT * from job_specifications WHERE id = ?"
        (:job_specification_id job)]
       (jdbc/query (rdbms/get-ds))
       first))

;(-> "5147259f-7210-4c4b-bfaa-cf3fe053b4fc" get-job get-job-spec)


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
          (if-let [contexts (:contexts context)]
            (build-tasks-for-contexts-sequence
               (convert-to-array contexts) task-defaults script-defaults)
            [])))

(defn build-tasks-for-contexts-sequence
  "Build the tasks for a sequence of contexts."
  [context inherited-task-defaults inherited-script-defaults]
  (apply concat
         (map
           (fn [context]
             (logging/debug {:context context})
             (let [task-defaults (deep-merge inherited-task-defaults
                                                  (or (:task_defaults context)
                                                      {}))
                   script-defaults (deep-merge inherited-script-defaults
                                                    (or (:script_defaults context)
                                                        {}))]
               (build-tasks-for-single-context context
                                               task-defaults
                                               script-defaults)))
           context)))

(def ^:private script-base-defaults {})

(defn build-tasks
  "Build the tasks for the given top-level context spec"
  [tx job context-spec]
  (let [context-spec (clojure.walk/keywordize-keys context-spec)
        tasks (build-tasks-for-single-context
                context-spec
                (conj (or (:task_defaults context-spec) {})
                      {:job_id (:id job)})
                (deep-merge script-base-defaults
                            (or (:script_defaults context-spec) {})))]
    (doseq [raw-task tasks]
      (task/create-db-task tx raw-task))))


;### create tasks for job ###############################################

(defn create-tasks [job spec]
  (jdbc/with-db-transaction [tx (rdbms/get-ds)]
    (let [context-spec (-> spec :data :context)]
      (build-tasks tx job context-spec))))

(defn number-of-tasks [job]
  (->> ["SELECT count(*) AS count FROM tasks WHERE job_id = ?"
        (:id job)]
       (jdbc/query (rdbms/get-ds))
       first
       :count))

(defn- check-tasks-empty! [job spec]
  (when (= 0 (number-of-tasks job))
    (let [job-id (:id job)]
      (jdbc/insert!
        (rdbms/get-ds) :job_issues
        {:job_id (:id job)
         :type "warning"
         :title "No Tasks Have Been Created"
         :description "This job has **no tasks** and has been set to defective state."})
      (jdbc/update!
        (rdbms/get-ds) :jobs
        {:state "defective"}
        ["id = ? " job-id])
      (messaging/publish "job.updated" {:id job-id :state "defective"}))))

(defn create-tasks-and-trials [message]
  (when-let [job (get-job (:job_id message))]
    (when-let [job-spec-row (get-job-spec job)]
      (wrap-exception-create-job-issue
        job "An exception occurred when creating tasks and trials!"
        (create-tasks job job-spec-row)
        (doseq [task (jdbc/query
                       (rdbms/get-ds)
                       ["SELECT id, state FROM tasks WHERE job_id = ?"
                        (:id job)])])
        (check-tasks-empty! job (:data job-spec-row))))))

;### initialization ###########################################################
(defn initialize []
  (logging/debug "initialize")
  (catcher/with-logging {}
    (messaging/listen "job.create-tasks-and-trials"
                      #'create-tasks-and-trials
                      "job.create-tasks-and-trials")))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
