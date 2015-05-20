; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.trigger
  (:require 
    [cider-ci.builder.dotfile :as dotfile]
    [cider-ci.builder.jobs :as jobs]
    [cider-ci.builder.jobs.filter :as jobs.filter]
    [cider-ci.builder.jobs.tags :as tags]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge convert-to-array]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


;### include-exclude-filter ###################################################


(defprotocol Pattern
  (to-pattern [x]))

(extend-protocol Pattern
  java.lang.String
  (to-pattern [x] (re-pattern x))
  java.util.regex.Pattern
  (to-pattern [x] x)
  nil
  (to-pattern [x] nil)
  java.lang.Boolean
  (to-pattern [x] x))


(defn include-exclude-filter [include-match exclude-match coll]
  (->> coll
       (filter #(and include-match
                     (re-find (to-pattern include-match) (str %))))
       (filter #(or (not exclude-match) 
                    (not (re-find (to-pattern exclude-match) (str %)))))))

;(include-exclude-filter "master" "^mk" ["ts_master" "mk_master" ])

;### trigger jobs #######################################################

(defn event-branch-updated-fits-trigger? [event-data trigger]
  (->> [(:name event-data)]
       (include-exclude-filter 
         (:include-match trigger)
         (:exclude-match trigger))
       first 
       boolean))

(defn event-job-updated-fits-trigger? [event-data trigger]
  ; TODO, there seems to be an implementation missing  ??? 
  true)

(defn find-trigger-for-event [event-data triggers]
  (some #(and (= (:type %) (:type event-data)) %) triggers))

(defn some-job-trigger-matches-event? [event-data job]
  (let [triggers (:triggers job)]
    (if (= true triggers)
      true
      (when-let [matching-trigger (find-trigger-for-event event-data triggers)]
        (case (:type event-data)
          "branch.updated" (event-branch-updated-fits-trigger? event-data matching-trigger)
          "job.updated" (event-job-updated-fits-trigger? event-data matching-trigger)
          (do (logging/warn "not handled job-trigger event" event-data)
            false))))))

(defn filter-jobs-by-triggers [event-data jobs]
  (filter #(some-job-trigger-matches-event? event-data %) jobs))
  
(defn- trigger-jobs [event-data]
  (let [tree-id (:tree_id event-data)]
    (->> (dotfile/get-dotfile tree-id)
         (debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         :jobs
         (debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         convert-to-array
         (into [])(debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         (map #(assoc % :tree_id tree-id))
         (into [])(debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         (filter #(-> % :triggers))
         (into [])(debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         (filter jobs.filter/dependencies-fullfiled?)
         (into [])(debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         (filter-jobs-by-triggers event-data)
         (into [])(debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         ;(filter trigger-constraints-fullfilled?)
         (map jobs/create)
         (debug/identity-with-logging 'cider-ci.builder.jobs.trigger)
         doall)))


;### listen to branch updates #################################################

(defn- evaluate-branch-updated-message [msg]
  (catcher/wrap-with-log-warn
    (logging/debug 'evaluate-branch-updated-message {:msg msg})
    (-> (jdbc/query 
          (rdbms/get-ds) 
          ["SELECT tree_id FROM commits WHERE id = ? " (:current_commit_id msg)])
        first
        (#(trigger-jobs (conj msg 
                              (select-keys % [:tree_id]) 
                              {:type "branch.updated"}))))))

(defn listen-to-branch-updates-and-fire-trigger-jobs []
  (messaging/listen "branch.updated" evaluate-branch-updated-message))


;### listen to job updates ##############################################

(defn evaluate-job-update [msg] 
  (-> (jdbc/query 
        (rdbms/get-ds) 
        ["SELECT * FROM jobs WHERE id = ? " (:id msg)])
      first
      (#(trigger-jobs (conj % msg {:type "job.updated"})))))


(defn listen-to-job-updates-and-fire-trigger-jobs []
  (messaging/listen "job.updated"  evaluate-job-update))

;### initialize ###############################################################

(defn initialize []
  (listen-to-branch-updates-and-fire-trigger-jobs)
  (listen-to-job-updates-and-fire-trigger-jobs)
  )


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
