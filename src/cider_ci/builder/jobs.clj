; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs
  (:require 
    [cider-ci.builder.jobs.filter :as jobs.filter]
    [cider-ci.builder.jobs.tags :as tags]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.util :as util :refer [builds-or-jobs]]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


;### create job #########################################################

(defn try-to-add-specification-from-dotfile [params] 
  (try 
    (-> (repository/get-path-content (:tree_id params) "/.cider-ci.yml")
        builds-or-jobs
        (get (keyword (:name params)))
        :job_specification
        ((fn [js] (assoc params :job_specification js))))
    (catch clojure.lang.ExceptionInfo e
      (case (-> e ex-data :object :status)
        404 params
        (throw e)))))

(defn add-specification-id-if-not-present [params]
  (if (:job_specification_id params)
    params
    (assoc params :job_specification_id 
           (-> params
               :job_specification
               spec/get-or-create-job-specification 
               :id))))

(defn add-specification-from-dofile-if-not-present [params]
  (if (and (not (:job_specification_id params))
           (not (:job_specification params))
           (:name params)
           (:tree_id params))
    (try-to-add-specification-from-dotfile params)
    params)
  )


(defn invoke-create-tasks-and-trials [params]
  (tasks/create-tasks-and-trials {:job_id (:id params)})
  params)

(defn persist-job [params]
  (->> (jdbc/insert! 
         (rdbms/get-ds)
         :jobs
         (select-keys params 
                      [:tree_id, :job_specification_id, 
                       :name, :description, :priority]))
       first
       (conj params)))

(defn create [params]
  (-> params 
      add-specification-from-dofile-if-not-present
      add-specification-id-if-not-present
      persist-job
      invoke-create-tasks-and-trials
      tags/add-job-tags))



;### available jobs #####################################################

(defn available-jobs [tree-id]
  (->> (repository/get-path-content tree-id "/.cider-ci.yml")
       builds-or-jobs
       (into [])
       (map (fn [[name_sym properties]] (assoc properties 
                                               :name (name name_sym)
                                               :tree_id tree-id)))
       (filter jobs.filter/dependencies-fullfiled?)
       ))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
