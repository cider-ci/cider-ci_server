; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.chaining
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


;### trigger jobs #######################################################

(defn trigger-constraints-fullfilled? [properties] 
    (let [query-atom (atom (hh/select :true))
          tree-id (:tree_id properties)]
      (logging/debug "trigger-constraints-fullfilled?" {:properties properties :initial-sql (hc/format @query-atom)})
      (jobs.filter/add-self-name-filter-to-query query-atom (:name properties) tree-id)
      (jobs.filter/add-branch-filter-to-query tree-id query-atom (-> properties :triggers))
      (logging/debug "trigger-constraints-fullfilled?" {:final-sql (hc/format @query-atom)})
      (->> (-> @query-atom
               (hc/format))
           (jdbc/query (rdbms/get-ds))
           first 
           :bool)))

(defn filter-triggers [jobs data]
  (logging/info 'filter-triggers [jobs data])
  jobs
  )
  
(defn- trigger-jobs [data]
  (logging/debug 'trigger-jobs data)
  (let [tree-id (:tree_id data)]
    (->> (dotfile/get-dotfile tree-id)
         :jobs
         convert-to-array
         (map #(assoc % :tree_id tree-id))
         (filter #(-> % :triggers))
         (filter jobs.filter/dependencies-fullfiled?)
         (filter-triggers data)
         ;(filter trigger-constraints-fullfilled?)
         (map jobs/create)
         doall)))

;(trigger-jobs "fd4f87460095ac66647e5bfc4fc56f7039a665c9") 
;(available-jobs "6ead70379661922505b6c8c3b0acfce93f79fe3e")


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
        ["SELECT tree_id FROM jobs WHERE id = ? " (:id msg)])
      first
      (#(trigger-jobs (conj msg 
                            (select-keys % [:tree_id]) 
                            {:type "job.updated"})))))

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
