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

        (instance?  clojure.lang.Seqable d)
        (->> d
             (map-indexed (fn [i v] [(or (:key v) (:name v) (str i)) v]))
             (into {}))

        :else (throw (IllegalStateException.
                       (str (type d) "  must a map or Seqable!")))))


;### Debug ####################################################################

(defn- normalize-body-tasks-to-script-tasks [tasks-map]
  (->> tasks-map
       (map (fn [[k v]]
              [k (cond (:scripts v) v
                       (:body v) (-> v
                                     (assoc :scripts {:main {:body (:body v)}})
                                     (dissoc :body))
                       :else  v)]))
       (into {})))

;### Debug ####################################################################

(defn- normalize-string-value-to-body-map [v]
  (if (instance? String v)
    {:body v}
    v))

(defn- normalize-seq-with-string-value-to-body-map [d]
  (cond (map? d)
        (->> d
             (map (fn [[k v]] [k (normalize-string-value-to-body-map v)]))
             (into {}))

        (instance?  clojure.lang.Seqable d)
        (->> d
             (map (fn [v] (normalize-string-value-to-body-map v))))

        :else (throw (IllegalStateException.
                       (str (type d) "  must a map or Seqable!")))))

;### Debug ####################################################################

(defn- normalize-context [context-spec]
  (-> context-spec
      (#(if (:tasks %) % (assoc % :tasks [])))
      (#(assoc % :tasks (normalize-seq-with-string-value-to-body-map (:tasks %))))
      (#(assoc % :tasks (normalize-to-key-name-map (:tasks %))))
      (#(assoc % :tasks (normalize-body-tasks-to-script-tasks (:tasks %))))
      (#(if-let[subcontexts (:subcontexts %)]
          (assoc % :subcontexts
                 (->> subcontexts
                      normalize-to-key-name-map
                      (map (fn [[k sctx]] [k  (normalize-context sctx)]))
                      (into {})))
          %))
      ))



;### Debug ####################################################################


(def CONTEXT-KEYS [:tasks :task-defaults :script-defaults :subcontexts])

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

(apply dissoc {:x 5} [:x])

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)
