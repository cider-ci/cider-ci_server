; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.normalizer
  (:require

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [÷> ÷>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(defn seqable? [x]
  (instance? clojure.lang.Seqable x))

(defn- normalize-to-key-name-map [d]
  (cond (map? d)
        (->> d
             (map (fn [[k v]]
                    (when-not (map v) (throw (IllegalStateException.
                                               (str v " must be a map!"))))
                    [k (-> v
                           (assoc :key (or (:key v) k))
                           (assoc :name (or (:name v) k)))]))
             (into {}))
        (seqable? d)
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

(defn seq-to-strin-bool-map [sq]
  (->> sq
       (map (fn [s] [(str s) true]))
       (into {})))

(defn- normalize-traits-in-task [task-map]
  (if-let [traits (:traits task-map)]
    (cond (map? traits) task-map
          (seqable? traits) (assoc task-map :traits (seq-to-strin-bool-map traits))
          :else (throw (IllegalStateException. "Traits must be a map or a seqable.")))
    (assoc task-map :traits {})))

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

        (seqable? d)
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


;### task-defaults ############################################################

(defn- normalize-task-defaults [task-defaults]
  (normalize-task task-defaults))

(defn- normalize-task-defaults-in-context [ctx]
  (if-let [task-defaults (:task-defaults ctx)]
    (assoc ctx :task-defaults (normalize-task-defaults task-defaults))
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



;### Debug ####################################################################


(def CONTEXT-KEYS [:task :tasks :task-defaults :script-defaults :subcontexts])

(defn- normalize-to-top-level-context [spec]
  (if (:context spec)
    spec
    (-> spec
        (assoc :context (select-keys spec CONTEXT-KEYS))
        (#(apply dissoc % CONTEXT-KEYS))
        )))

;### Debug ####################################################################

(defn normalize-job-spec [job-spec]
  (-> job-spec
      normalize-to-top-level-context
      (#(assoc % :context (-> % :context normalize-context)))
      ))

;(apply dissoc {:x 5} [:x])

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)
