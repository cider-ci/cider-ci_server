; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.tasks
  (:require 
    [cider-ci.builder.task :as task]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.expansion :as expansion]
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
(defmacro wrap-exception-create-execution-issue [execution description & body]
  `(try 
     ~@body
     (catch Exception e#
       (let [row-data#  {:execution_id (:id ~execution) 
                         :description ~description
                         :stacktrace  (exception/stringify e# "\\n")}]
         (logging/warn ~execution row-data# e#)
         (jdbc/insert! (rdbms/get-ds) "execution_issues" row-data#)))))

(defn get-execution [id]
  (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM executions WHERE id =?::UUID" id ])))


;### build tasks ##############################################################
(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn build-scripts [task script-defaults]
  (into {} (for [[name-key script] (or (:scripts task) {})]
             (let [final-script  (deep-merge script-defaults script)]
               [name-key final-script]))))

(defn build-task [task-spec task-defaults script-defaults name-prefix]
  (let [merged-task (deep-merge task-defaults task-spec)]
    ;(logging/debug {:merged-task merged-task})
    (conj merged-task
          {:scripts (build-scripts merged-task script-defaults)
           :name (str name-prefix " » " (:name merged-task))
           })))

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
                 (or (:tasks context) []))
            (if-let [subcontexts-spec (:subcontexts context)]
              (build-tasks-for-contexts-sequence subcontexts-spec 
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
             (let [task-defaults (deep-merge inherited-task-defaults
                                             (or (:task_defaults context) 
                                                 {}))
                   script-defaults (deep-merge inherited-script-defaults
                                               (or (:script_defaults context) 
                                                   {}))]
               (build-tasks-for-single-context context 
                                               task-defaults 
                                               script-defaults
                                               name-prefix)))
           context-spec)))

(defn build-tasks 
  "Build the tasks for the given top-level specification."
  [execution _spec]
  (let [spec (clojure.walk/keywordize-keys _spec)
        tasks (build-tasks-for-single-context 
                spec
                (conj (or (:task_defaults spec) {})
                      {:execution_id (:id execution)})
                (or (:script_defaults spec) {})
                "")] 
    (doseq [raw-task tasks]
      (wrap-exception-create-execution-issue 
        execution "Error during task creation" 
        (task/create-db-task raw-task)))))

(defn expand-execution-spec [execution]
  (wrap-exception-create-execution-issue 
    execution "Error during expansion" 
    (let [original-spec (spec/get-execution-spec (:specification_id execution))
          substituded-spec-data (expansion/expand (:tree_id execution) 
                                                  (clojure.walk/keywordize-keys
                                                    (:data original-spec)))
          expanded-spec (spec/get-or-create-execution-specification 
                          substituded-spec-data)]
      (logging/debug {:original-spec original-spec 
                      :substituded-spec-data substituded-spec-data
                      :expanded-spec expanded-spec})
      (jdbc/update! (rdbms/get-ds) :executions
                    {:expanded_specification_id (:id expanded-spec)}
                    ["id = ? " (:id execution)])
      (get-execution (:id execution)))))


;### create tasks for execution ###############################################
(defn create-tasks [execution] 
  (wrap-exception-create-execution-issue 
    execution "Error when creating tasks" 
    (let [spec (-> (jdbc/query (rdbms/get-ds) 
                               ["SELECT * FROM specifications WHERE id = ?" 
                                (:expanded_specification_id execution)])
                   first :data)]
      (build-tasks execution spec))))


;### messaging ################################################################
(defn create-tasks-and-trials [message]
  (logging/debug create-tasks-and-trials message)
  (if-let [execution (get-execution (:execution_id message))] 
    (wrap-exception-create-execution-issue 
      execution "Error in create-tasks-and-trials" 
      (-> execution expand-execution-spec create-tasks)
      (doseq [task-with-id (jdbc/query 
                             (rdbms/get-ds) 
                             ["SELECT id FROM tasks WHERE execution_id = ?::UUID" 
                              (:id execution)])]
        (messaging/publish "task.create-trials" task-with-id)))
    (throw (IllegalStateException. (str "could not find execution for " message)))))

;(create-tasks-and-trials {:execution_id "fb1e97df-64de-4c12-bf01-4778924bbae9"})

;### initialization ###########################################################
(defn initialize []
  (logging/debug "initialize")
  (with/logging
    (messaging/listen "execution.create-tasks-and-trials" 
                      #'create-tasks-and-trials 
                      "execution.create-tasks-and-trials")
    ))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
