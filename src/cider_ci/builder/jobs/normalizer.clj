; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.normalizer
  (:require
    [cider-ci.utils.core :refer :all]

    [clj-time.core :as time]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(defn- normalize-to-key-name-map [d]
  (cond (map? d)
        (->> d
             (map (fn [[k v]]
                    (when-not (map v) (throw (IllegalStateException.
                                               (str v " must be a map!"))))
                    [k (-> v
                           (assoc :key (to-cistr (or (:key v) k)))
                           (assoc :name (to-cistr (or (:name v) k))))]))
             (into {}))
        (coll? d)
        (->> d
             (map-indexed (fn [i v] [(or (:key v) (:name v) (str i)) v]))
             (into {}))

        :else (throw (IllegalStateException.
                       (str (type d) "  must a map or Seqable!")))))


;### Debug ####################################################################

(defn- normalize-string-value-to-body-map [v]
  (if (string? v)
    {:body v}
    v))

;### normalize task ###########################################################

(defn- normalize-traits-in-task [task-map]
  (assoc
    task-map :traits
    (to-cisetmap (or (:traits task-map) {}))))

(defn- normalize-task-with-body-to-scripts-task [task-map]
  (cond (:scripts task-map) task-map
        (:body task-map) (-> task-map
                             (assoc :scripts {:main {:body (:body task-map)}})
                             (dissoc :body))
        :else  task-map))


(defn- normalize-task [task]
  (-> task
      normalize-string-value-to-body-map
      normalize-task-with-body-to-scripts-task
      normalize-traits-in-task
      ))



;### normalize tasks ##########################################################

(defn- normalize-seq-with-string-value-to-body-map [d]
  (cond (map? d)
        (->> d
             (map (fn [[k v]] [k (normalize-string-value-to-body-map v)]))
             (into {}))

        (coll? d)
        (->> d
             (map (fn [v] (normalize-string-value-to-body-map v))))

        :else (throw (IllegalStateException.
                       (str (type d) "  must a map or Seqable!")))))


(defn- normalize-tasks [tasks]
  (->> tasks
       normalize-seq-with-string-value-to-body-map
       normalize-to-key-name-map
       (map (fn [[k task]]
              [k (normalize-task task)]))
       (into {})
       ))

(defn- normalize-task-to-tasks [context-spec]
  (cond
    (:task context-spec) (-> context-spec
                             (dissoc :task)
                             (assoc :tasks [(:task context-spec)]))
    (:tasks context-spec) context-spec
    :else (assoc context-spec :tasks {})))

(defn- normalize-tasks-in-context [context-spec]
  (when (and (:task context-spec)
             (:tasks context-spec))
    (throw (IllegalStateException. "The keys 'task' and 'tasks' are exclusive.")))
  (-> context-spec
      normalize-task-to-tasks
      (#(assoc % :tasks (normalize-tasks (:tasks %))))))


;### task_defaults ############################################################

(defn- normalize-task-defaults [task-defaults]
  (normalize-task task-defaults))

(defn- normalize-task-defaults-in-context [ctx]
  (if-let [task-defaults (:task_defaults ctx)]
    (assoc ctx :task_defaults (normalize-task-defaults task-defaults))
    ctx
    ))

;### Debug ####################################################################

(defn- normalize-context [context-spec]
  (-> context-spec
      normalize-tasks-in-context
      normalize-task-defaults-in-context
      (#(if-let[subcontexts (:subcontexts %)]
          (assoc % :subcontexts
                 (->> subcontexts
                      normalize-to-key-name-map
                      (map (fn [[k sctx]] [k  (normalize-context sctx)]))
                      (into {})))
          %))
      ))


;### Normalize job properties #################################################

(defn- normalize-empty-tasks-warning [spec]
  "Adds :empty_tasks_warning `true` if the key is not present.
  Otherwise normalizes the value of :empty_tasks_warning to a boolean."
  (let [value (if-not (contains? spec :empty_tasks_warning)
                true
                (if (:empty_tasks_warning spec)
                  true
                  false))]
    (assoc spec :empty_tasks_warning value)))

(defn- normalize-job-properties [spec]
  (-> spec
      normalize-empty-tasks-warning))

;### Normalize to top level context ###########################################

(def CONTEXT-KEYS [:task :tasks :task_defaults :script_defaults :subcontexts])

(defn- normalize-to-top-level-context [spec]
  (if (:context spec)
    spec
    (-> spec
        (assoc :context (select-keys spec CONTEXT-KEYS))
        (#(apply dissoc % CONTEXT-KEYS))
        )))

;### Debug ####################################################################

; TODO catch exception and convert it to a halfway meaning full
; exception for a tree issue
(defn normalize-job-spec [job-spec]
  (try
    (-> job-spec
        normalize-to-top-level-context
        normalize-job-properties
        (#(assoc % :context (-> % :context normalize-context)))
        )
    (catch clojure.lang.ExceptionInfo e (throw e))
    (catch Throwable e
      (let [title "Project Configuration Normalization Error"]
        ( ->> {:title title
               :description (str "An unexpected \"_" (.getMessage e)"_\" exception"
                                 "occurred during normalization of the job. **Check your
                                 Cider-CI project configuration.**
                                 The server log-files might give a hint
                                 if the problem is not obvious. Ask your server administrator
                                 to check the _dispatcher logs_ at about " (time/now) "."
                                 )
               :type "error" }
              (ex-info title)
              throw )))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
