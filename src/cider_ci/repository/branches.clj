; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.branches
  (:require
    [cider-ci.repository.commits :as commits]
    [cider-ci.repository.sql.branches :as sql.branches]
    [drtom.logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


;### create-new db branches ###################################################

(defn- get-existing-db-branches [tx repository-id]
  (set (map :name (set (sql.branches/for-repository tx repository-id)))))

(defn- filter-to-be-created-branches [git-branches existing-db-branches]
  (filter
    (fn [git-branch] (not (contains? existing-db-branches (:name git-branch))))
    git-branches))

(defn- update-or-create-branches-commits [tx db-branch]
  ; call stored procedure to do this
  (jdbc/query tx ["SELECT update_branches_commits(?,?,?)"
                  (:id db-branch)
                  (:current_commit_id db-branch)
                  nil]))

(defn- create-new-db-branch [tx repository-path repository-id git-branch]
  (let [commit-id (:current_commit_id git-branch)
        current_commit (commits/import-recursively tx commit-id repository-path)
        db-branch (first (sql.branches/create!
                           tx (assoc git-branch :repository_id repository-id)))]
    (update-or-create-branches-commits tx db-branch)
    db-branch))

(defn create-new [tx git-branches repository-id repository-path]
  (let [existing-db-branches (get-existing-db-branches tx repository-id)
        to-be-created (filter-to-be-created-branches
                        git-branches existing-db-branches)]
    (doall (map #(create-new-db-branch tx repository-path repository-id %)
                to-be-created))))


;##############################################################################

(defn- to-be-updated
  [git-branches existing-branches]
  (filter (fn [git-branch]
            (let [name (:name git-branch)
                  corresponding-existing (first (filter
                                                  (fn [existing-branch]
                                                    (= name (:name existing-branch)))
                                                  existing-branches))]
              ; TODO corresponding-existing has only name attribute
              (if-not corresponding-existing
                false
                (not= (:current_commit_id corresponding-existing) (:current_commit_id git-branch)))))
          git-branches))


(defn update-outdated [tx git-branches canonic-id repository-path]
  (let [existing-branches (sql.branches/for-repository tx canonic-id)
        to-be-updated (to-be-updated git-branches existing-branches)]
    (doall (map (fn [git-branch]
                  (let [branch (first (jdbc/query tx ["SELECT * FROM branches WHERE
                                                      repository_id = ? AND name = ?"
                                                      canonic-id
                                                      (:name git-branch)]))
                        _ (commits/import-recursively tx (:current_commit_id git-branch) repository-path)

                        update_result (jdbc/update! tx :branches
                                                    (select-keys git-branch [:current_commit_id])
                                                    ["repository_id = ? AND name = ?" canonic-id (:name git-branch)])

                        update_branches_commits_result (jdbc/query tx ["SELECT update_branches_commits(?,?,?)"
                                                                       (:id branch)
                                                                       (:current_commit_id git-branch)
                                                                       (:current_commit_id branch)])

                        updated_branch (first (jdbc/query tx ["SELECT * FROM branches WHERE
                                                              repository_id = ? AND name = ?"
                                                              canonic-id
                                                              (:name git-branch)]))]
                    updated_branch))
                to-be-updated))))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
