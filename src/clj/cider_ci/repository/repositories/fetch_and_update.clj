; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories.fetch-and-update
  (:require
    [cider-ci.repository.branches :as branches]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.repositories.shared :refer :all]
    [cider-ci.repository.state :as state]

    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]

    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [honeysql.sql :refer :all]
    [me.raynes.fs :as fs]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
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
  (catcher/with-logging {}
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (let [git-branches (get-git-branches path)
            canonic-id (git.repositories/canonic-id repository)]
        {:created (branches/create-new tx git-branches canonic-id path)
         :updated (branches/update-outdated tx git-branches canonic-id path)
         :deleted (delete-removed-branches tx git-branches (:git_url repository))}))))


;### git url ##################################################################

(defn git-url [repository]
  (:git_url repository)
  )

;### GIT Stuff ################################################################

(defn set-repo-state [id state]
  (swap! state/db
         (fn [db id]
           (assert (get (:repositories db) id))
           (assoc-in db [:repositories id :state] state))
         (str id)))

(defn set-repo-issue [id issue-key properties]
  (swap! state/db assoc-in
         [:repositories (str id) :issues issue-key] properties))

(defn unset-repo-issue [id issue-key]
  (swap! state/db update-in [:repositories (str id) :issues] dissoc issue-key))

(defn swap-in-repo-value [id k v]
  (swap! state/db
         (fn [db id k v]
           (assert (get (:repositories db) id))
           (assoc-in db [:repositories id k] v))
         (str id) k v))

(defn- git-fetch [repository path]
  (try
    (set-repo-state (:id repository) "fetching")
    (when-not (fs/exists? path)
      (system/exec! ["git" "init" "--bare" path]))
    (system/exec!
      ["git" "fetch" (git-url repository) "--force" "--tags" "--prune"  "+*:*"]
      {:timeout "10 Minutes", :dir path, :env {"TERM" "VT-100"}})
    (system/exec!
      ["git" "update-server-info"]
      {:dir path :env {"TERM" "VT-100"}})
    (swap-in-repo-value (:id repository) :last_fetched_at (time/now))
    (unset-repo-issue (:id repository) "fetch-error")
    (state/update-repo-branches (:id repository))
    true
    (catch Exception e
      (swap-in-repo-value (:id repository) :last_fetch_failed_at (time/now))
      (set-repo-issue (:id repository) "fetch-error"
                      {:title "Exception during git fetch"
                       :description (str e) })
      (throw e))
    (finally (set-repo-state (:id repository) "idle"))))

(defn fetch-and-update [repository]
  (debug/with-logging
    {}
    (future
      (locking (str "fetch-and-update_" (-> repository :id str))
        (let [path (repository-fs-path repository)]
          (and (git-fetch repository path)
               (update-branches repository path)))))))


;(->> ["SELECT * FROM repositories"] (jdbc/query (rdbms/get-ds)) first fetch-and-update)

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
