; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.executions
  (:require 
    [cider-ci.builder.executions.filter :as executions.filter]
    [cider-ci.builder.executions.tags :as tags]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.util :as util :refer [builds-or-executions]]
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


;### create execution #########################################################

(defn try-to-add-specification-from-dotfile [params] 
  (try 
    (-> (repository/get-path-content (:tree_id params) "/.cider-ci.yml")
        builds-or-executions
        (get (keyword (:name params)))
        :specification
        ((fn [specification]
           (assoc params :specification specification))))
    (catch clojure.lang.ExceptionInfo e
      (case (-> e ex-data :object :status)
        404 params
        (throw e)))))

(defn add-specification-id-if-not-present [params]
  (logging/info add-specification-id-if-not-present [params])
  (if (:specification_id params)
    params
    (assoc params :specification_id 
           (-> params
               :specification
               spec/get-or-create-execution-specification 
               :id))))

(defn add-specification-from-dofile-if-not-present [params]
  (if (and (not (:specification_id params))
           (not (:specification params))
           (:name params)
           (:tree_id params))
    (try-to-add-specification-from-dotfile params)
    params)
  )


(defn invoke-create-tasks-and-trials [params]
  (tasks/create-tasks-and-trials {:execution_id (:id params)})
  params)

(defn persist-execution [params]
  (->> (jdbc/insert! 
         (rdbms/get-ds)
         :executions
         (select-keys params 
                      [:tree_id, :specification_id, 
                       :name, :description, :priority]))
       first
       (conj params)))

(defn create [params]
  (logging/info create [params])
  (-> params 
      add-specification-from-dofile-if-not-present
      add-specification-id-if-not-present
      persist-execution
      invoke-create-tasks-and-trials
      tags/add-execution-tags))



;### available executions #####################################################

(defn available-executions [tree-id]
  (->> (repository/get-path-content tree-id "/.cider-ci.yml")
       builds-or-executions
       (into [])
       (map (fn [[name_sym properties]] (assoc properties 
                                               :name (name name_sym)
                                               :tree_id tree-id)))
       (filter executions.filter/dependencies-fullfiled?)
       ))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
