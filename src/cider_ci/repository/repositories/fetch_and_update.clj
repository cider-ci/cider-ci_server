; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories.fetch-and-update
  (:require
    [cider-ci.repository.branches :as branches]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.sql :refer :all]
    [me.raynes.fs :as fs]

    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]

    ))


;### helpers ##################################################################

(defn- directory-exists? [path]
  (let [file (clojure.java.io/file path)]
    (and (.exists file)
         (.isDirectory file))))

(defn- assert-directory-exists! [path]
  (when-not (directory-exists? path)
    (throw (IllegalStateException. "Directory does not exist."))))


;### delete branches ##########################################################

(defn- branches-delete-query [git-url existing-branches-names]
  (-> (sql-delete-from :branches)
      (sql-using :repositories)
      (sql-merge-where [:= :repositories.id :branches.repository_id])
      (sql-merge-where [:= :repositories.git-url git-url])
      (sql-merge-where [:not-in :branches.name existing-branches-names])
      (sql-returning :branches.name :repositories.id :repositories.git_url)
      sql-format
      ))

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
           (system/exec-with-success-or-throw
             ["git" "branch" "--list" "--no-abbrev" "--no-color" "-v"]
             {:timeout "1 Minutes", :dir repository-path, :env {"TERM" "VT-100"}})
           :out
           (clojure.string/split #"\n"))
       (map (fn [line]
              (let [[_ branch-name current-commit-id]
                    (re-find #"^?\s+(.+)\s+([0-9a-f]{40})\s+(.*)$" line)]
                {:name (clojure.string/trim branch-name)
                 :current_commit_id current-commit-id})))))

(defn- update-branches [repository]
  (catcher/with-logging {}
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (let [repository-path (git.repositories/path repository)
            git-branches (get-git-branches repository-path)
            canonic-id (git.repositories/canonic-id repository)]
        {:created (branches/create-new tx git-branches canonic-id repository-path)
         :updated (branches/update-outdated tx git-branches canonic-id repository-path)
         :deleted (delete-removed-branches tx git-branches (:git_url repository))}))))


;### GIT Stuff ################################################################

(defn- update-git-server-info [repository]
  (logging/debug update-git-server-info [repository])
  (let [repository-path (git.repositories/path repository)
        id (git.repositories/canonic-id repository) ]
    (system/exec-with-success-or-throw ["git" "update-server-info"]
                                       {:timeout "1 Minute", :dir repository-path, :env {"TERM" "VT-100"}})))

(defn- send-branch-update-notification [action branch]
  (let [queue-name (str "branch." (name action))]
    (messaging/publish queue-name branch)))

(defn- send-branch-update-notifications [updated-branches]
  (catcher/with-logging {}
    (doseq [action [:created :deleted :updated]]
      (->> (action updated-branches)
           (map #(send-branch-update-notification action %))
           ;(map #(messaging/publish (str "branch." (name action)) %))
           doall))))

(defn send-repository-update-notification [repository]
  (messaging/publish "repository.updated"
                     (select-keys repository [:git_url :name])))

(defn git-update [repository]
  (catcher/with-logging {}
    (let [dir (git.repositories/path repository)
          _ (assert-directory-exists! dir)
          updated-branches (update-branches repository)]
      (update-git-server-info repository)
      (send-branch-update-notifications updated-branches)
      (when (or (seq (:created updated-branches))
                (seq (:deleted updated-branches))
                (seq (:updated updated-branches)))
        (send-repository-update-notification repository)))))

(defn git-initialize [repository]
  (catcher/with-logging {}
    (let [dir (git.repositories/path repository)]
      (system/exec-with-success-or-throw ["rm" "-rf" dir])
      (system/exec-with-success-or-throw
        ["git" "clone" "--mirror" (:git_url repository) dir]
        {:timeout "30 Minutes"}))))

(defn- git-fetch [repository path]
  (system/exec-with-success-or-throw
    ["git" "fetch" (:git_url repository) "--force" "--tags" "--prune"  "+*:*"]
    {:timeout "10 Minutes", :dir path, :env {"TERM" "VT-100"}}))

(defn  git-fetch-or-initialize [repository]
  (catcher/snatch {}
    (let [path (git.repositories/path repository)]
      (if (fs/exists? path)
        (git-fetch repository path)
        (git-initialize repository)))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
