; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.executions.chaining
  (:require 
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.executions :as executions]
    [cider-ci.builder.executions.filter :as executions.filter]
    [cider-ci.builder.executions.tags :as tags]
    [cider-ci.builder.spec :as spec]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.util :as util :refer [builds-or-executions]]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [cider-ci.utils.map :refer [deep-merge]]
    [clj-logging-config.log4j :as logging-config]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


;### trigger executions #######################################################

(defn trigger-constraints-fullfilled? [properties] 
    (let [query-atom (atom (hh/select :true))
          tree-id (:tree_id properties)]
      (logging/debug "trigger-constraints-fullfilled?" {:properties properties :initial-sql (hc/format @query-atom)})
      (executions.filter/add-self-name-filter-to-query query-atom (:name properties) tree-id)
      (executions.filter/add-branch-filter-to-query tree-id query-atom (-> properties :trigger))
      (logging/debug "trigger-constraints-fullfilled?" {:final-sql (hc/format @query-atom)})
      (->> (-> @query-atom
               (hc/format))
           (jdbc/query (rdbms/get-ds))
           first 
           :bool)))
  
(defn trigger-executions [tree-id]
  (->> (repository/get-path-content tree-id "/.cider-ci.yml")
       builds-or-executions
       (map (fn [[name_sym properties]] (assoc properties 
                                               :name (name name_sym)
                                               :tree_id tree-id)))
       (filter #(-> % :trigger))
       (filter executions.filter/dependencies-fullfiled?)
       (filter trigger-constraints-fullfilled?)
       (map executions/add-specification-id-if-not-present)
       (map executions/create)
       doall))

;(trigger-executions "6ead70379661922505b6c8c3b0acfce93f79fe3e")
;(available-executions "6ead70379661922505b6c8c3b0acfce93f79fe3e")


;### listen to branch updates #################################################

(defn- evaluate-branch-updated-message [msg]
  (-> (jdbc/query 
        (rdbms/get-ds) 
        ["SELECT tree_id FROM commits WHERE id = ? " (:current_commit_id msg)])
      first
      :tree_id
      trigger-executions))

(defn listen-to-branch-updates-and-fire-trigger-executions []
  (messaging/listen "branch.updated" evaluate-branch-updated-message))


;### listen to execution updates ##############################################

(defn evaluate-execution-update [msg] 
  (-> (jdbc/query 
        (rdbms/get-ds) 
        ["SELECT tree_id FROM executions WHERE id = ? " (:id msg)])
      first
      :tree_id
      trigger-executions))

(defn listen-to-execution-updates-and-fire-trigger-executions []
  (messaging/listen "execution.updated"  evaluate-execution-update))

;### initialize ###############################################################

(defn initialize []
  (listen-to-branch-updates-and-fire-trigger-executions)
  (listen-to-execution-updates-and-fire-trigger-executions)
  )


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
