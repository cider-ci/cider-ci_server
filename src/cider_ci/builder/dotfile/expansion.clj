; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile.expansion
  (:require 
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.util :as util]
    [cider-ci.utils.map :as map]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [drtom.logbug.debug :as debug]
    ))


(declare expand-context)


(defn file-name-to-task [file-name]
  [file-name {:environment-variables
              {:CIDER_CI_TASK_FILE file-name}}])

(defn expand-tasks [git-ref-id context]
  (if-let [expand-spec (:_cider-ci_expand-to-tasks context)]
    (let [file-list (repository/ls-tree git-ref-id expand-spec)
          expanded-tasks (->> file-list
                              (map file-name-to-task) 
                              (into {}))
        
          tasks (if-let [existing-tasks (:tasks context)]
                  (cond (map? existing-tasks)(util/deep-merge 
                                               expanded-tasks existing-tasks)
                        :else (throw (IllegalStateException. 
                                       "tasks must be a map to be merged with expanded-tasks")))
                  expanded-tasks)] 
      (-> context
          (assoc :tasks tasks)
          (dissoc :_cider-ci_expand-to-tasks)))
    context))

(defn map-map-or-coll [f moc]
  (cond (map? moc) (->> moc  
                        (map (fn [[k v]] [k (f v)]))
                        (into {}))
        (coll? moc) (->> moc  (map f))
        :else (throw (IllegalStateException. "moc must be either a map or coll"))))

;(map-map-or-coll identity {:a 42})
;(map-map-or-coll identity [:a 42])

(defn expand-subcontexts [git-ref-id context]
  (if-let [subcontexts (:subcontexts context)]
    (assoc context 
           :subcontexts 
           (map-map-or-coll (fn [subcontext] 
                              (expand-context git-ref-id subcontext)) subcontexts))
    context))

(defn expand-context [git-ref-id context]
  (->> context
       (expand-tasks git-ref-id)
       (expand-subcontexts git-ref-id)))

(defn expand [git-ref-id spec]
  (assoc spec 
         :jobs
         (map-map-or-coll (fn [job] (expand-context git-ref-id job))
                          (:jobs spec))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

