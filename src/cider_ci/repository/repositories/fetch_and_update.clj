; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories.fetch-and-update
  (:require
    [cider-ci.repository.branches :as branches]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.branches :as sql.branches]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [me.raynes.fs :as fs]
    ))


;### helpers ##################################################################
(defn- directory-exists? [path]
  (let [file (clojure.java.io/file path)]
    (and (.exists file)
         (.isDirectory file))))

(defn- assert-directory-exists! [path]
  (when-not (directory-exists? path)
    (throw (IllegalStateException. "Directory does not exist."))))


;### branches #################################################################
(defn- get-git-branches [repository-path]
  (let [res (system/exec-with-success-or-throw
              ["git" "branch" "--no-abbrev" "--no-color" "-v"]
              {:watchdog (* 1 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})
        out (:out res)
        lines (clojure.string/split out #"\n")
        branches (map (fn [line]
                        (let [[_ branch-name current-commit-id]
                              (re-find #"^?\s+(\S+)\s+(\S+)\s+(.*)$" line)]
                          {:name branch-name
                           :current_commit_id current-commit-id}))
                      lines)]
    branches))

(defn- update-or-create-branches [tx repository]
  (catcher/wrap-with-log-error
    (let [repository-path (git.repositories/path repository)
          git-branches (get-git-branches repository-path)
          canonic-id (git.repositories/canonic-id repository)]
      (logging/debug update-or-create-branches {:repository-path repository-path
                                                :git-branches git-branches
                                                :canonic-id canonic-id})
      (sql.branches/delete-removed tx git-branches canonic-id)
      (let [created (branches/create-new tx git-branches canonic-id repository-path)
            updated (branches/update-outdated tx git-branches canonic-id repository-path)]
        (concat created updated)))))


;### GIT Stuff ################################################################
(defn- update-git-server-info [repository]
  (logging/debug update-git-server-info [repository])
  (let [repository-path (git.repositories/path repository)
        id (git.repositories/canonic-id repository) ]
    (system/exec-with-success-or-throw ["git" "update-server-info"]
                 {:watchdog (* 10 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})))

(defn- send-branch-update-notifications [branches]
  (catcher/wrap-with-log-error
    (logging/debug send-branch-update-notifications [branches])
    (doseq [branch branches]
      (messaging/publish "branch.updated" branch))))

(defn  git-update [repository]
  (catcher/wrap-with-log-error
    (let [updated-branches (atom nil)
          dir (git.repositories/path repository)]
      (assert-directory-exists! dir)
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (update-git-server-info repository)
        (reset! updated-branches (update-or-create-branches tx repository)))
      (send-branch-update-notifications @updated-branches))))

(defn git-initialize [repository]
  (catcher/wrap-with-log-error
    (let [dir (git.repositories/path repository)]
      (system/exec-with-success-or-throw ["rm" "-rf" dir])
      (system/exec-with-success-or-throw
        ["git" "clone" "--mirror" (:git_url repository) dir]
        {:watchdog (* 5 60 1000)}))))

(defn- git-fetch [repository path]
  (system/exec-with-success-or-throw
    ["git" "fetch" (:git_url repository) "--force" "--tags" "--prune"  "+*:*"]
    {:watchdog (* 10 60 1000), :dir path, :env {"TERM" "VT-100"}}))

(defn  git-fetch-or-initialize [repository]
  (try (catcher/wrap-with-log-warn
         (let [path (git.repositories/path repository)]
           (if (fs/exists? path)
             (git-fetch repository path)
             (git-initialize repository))))
       (catch Exception e
         (logging/warn (thrown/stringify e)))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

