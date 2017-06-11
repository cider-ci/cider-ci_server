; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.branch-updates.update
  (:refer-clojure :exclude [str keyword update])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:import
    [java.util.concurrent Executors ExecutorService Callable])
  (:require
    [cider-ci.server.repository.branch-updates.shared :refer :all]
    [cider-ci.server.repository.branches :as branches]
    [cider-ci.server.repository.git.repositories :as git.repositories]
    [cider-ci.server.repository.shared :refer [repository-fs-path]]
    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]
    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [honeysql.helpers :refer [group]])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]))


;### delete branches ##########################################################

(defn- branches-delete-query [git-url existing-branches-names]
  (-> (sql/delete-from :branches)
      (sql/using :repositories)
      (sql/merge-where [:= :repositories.id :branches.repository_id])
      (sql/merge-where [:= :repositories.git-url git-url])
      (sql/merge-where [:not-in :branches.name existing-branches-names])
      (sql/returning :branches.name :repositories.id :repositories.git_url)
      sql/format))

(defn- delete-removed-branches [tx keep-git-branches git-url]
  (let [keep-branch-names (map :name keep-git-branches)
        query (branches-delete-query git-url keep-branch-names)
        res (jdbc/query tx query)]
    (logging/debug "deleted " res " branches")
    res))


;### branches #################################################################

(defn- get-git-branches [repository-path]
  (I>> identity-with-logging
       (I> identity-with-logging
           (system/exec!
             ["git" "branch" "--list" "--no-abbrev" "--no-color" "-v"]
             {:timeout "1 Minutes", :dir repository-path, :env {"TERM" "VT-100"}})
           :out
           (clojure.string/split #"\n"))
       (map (fn [line]
              (let [[_ branch-name current-commit-id]
                    (re-find #"^?\s+(.+)\s+([0-9a-f]{40})\s+(.*)$" line)]
                {:name (clojure.string/trim branch-name)
                 :current_commit_id current-commit-id})))))

(defn- update-branches [repository path]
  (db-update-branch-updates (:id repository) #(assoc % :state "updating"))
  (Thread/sleep 1000)
  (jdbc/with-db-transaction [tx (rdbms/get-ds)]
    (let [git-branches (get-git-branches path)
          canonic-id (git.repositories/canonic-id repository)]
      ;(throw (ex-info "dobeldidoo" {}))
      {:created (branches/create-new tx git-branches canonic-id path)
       :updated (branches/update-outdated tx git-branches canonic-id path)
       :deleted (delete-removed-branches tx git-branches (:git_url repository))})))


;### update branches in db ####################################################

(defn update [repository]
  (let [repo-id (:id repository)
        path (repository-fs-path repository)
        update_info (update-branches repository path)
        params (->> (-> (sql/select [:%count.* :branches_count]
                                    [:%max.commits.committer_date :last_commited_at])
                        (sql/from :repositories)
                        (sql/merge-where [:= :repositories.id repo-id])
                        (sql/merge-join :branches [:= :repositories.id :branches.repository_id])
                        (sql/merge-join :commits [:= :branches.current_commit_id :commits.id])
                        (group :repositories.id)
                        sql/format)
                    (jdbc/query (rdbms/get-ds))
                    first)]
    (when params
      (db-update-branch-updates
        repo-id #(deep-merge % params
                             {:update_info update_info
                              :branches_updated_at (time/now)
                              :state "ok" })))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
