; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.executions.tags
  (:require 
    [cider-ci.builder.util :as util]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

(defn get-or-insert-tag [tag]
  (or (first (jdbc/query 
               (rdbms/get-ds)
               ["SELECT * FROM tags WHERE tag = ? " tag]))
      (first (jdbc/insert!
               (rdbms/get-ds) :tags
               {:tag tag}))))

(defn add-exectutions-tags-link [execution-params tag-params]
  (or (first (jdbc/query 
               (rdbms/get-ds)
               ["SELECT * FROM executions_tags 
                WHERE execution_id = ? 
                AND tag_id = ? " (:id execution-params ) (:id tag-params)]))
      (first (jdbc/insert!
               (rdbms/get-ds) :executions_tags
               {:execution_id (:id execution-params) :tag_id (:id tag-params)}))))

(defn get-branch-tags [params]
  (->> (jdbc/query 
         (rdbms/get-ds)
         ["SELECT name FROM branches
          JOIN commits ON commits.id = branches.current_commit_id
          WHERE commits.tree_id = ? " (:tree_id params)])
       (map :name)))

(defn get-repository-tags [params]
  (->> (jdbc/query 
         (rdbms/get-ds)
         ["SELECT repositories.name FROM repositories
          JOIN branches ON branches.repository_id = repositories.id
          JOIN commits ON commits.id = branches.current_commit_id
          WHERE commits.tree_id = ? " (:tree_id params)])
       (map :name)))

(defn get-tags [params]
  (concat 
    (get-branch-tags params)
    (get-repository-tags params)
    ))

(defn add-execution-tags [params]
  (doseq [tag (get-tags params)]
    (let [tag-row (get-or-insert-tag tag)]
      (add-exectutions-tags-link params tag-row)))
  params)


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
