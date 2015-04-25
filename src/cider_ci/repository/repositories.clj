; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories
  (:require 
    [cider-ci.repository.branches :as branches] 
    [cider-ci.repository.git.repositories :as git.repositories] 
    [cider-ci.repository.sql.branches :as sql.branches] 
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.thrown :as thrown]
    [cider-ci.utils.fs :as ci-fs]
    [me.raynes.fs :as fs]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))



;### helpers ##################################################################
(defn directory-exists? [path]
  (let [file (clojure.java.io/file path)] 
    (and (.exists file) 
         (.isDirectory file))))

(defn assert-directory-exists! [path]
  (when-not (directory-exists? path)
    (throw (IllegalStateException. "Directory does not exist."))))


;### repositories processors ##################################################
(defonce repository-processors-atom (atom {}))
(defn repository-agent-error-handler [_agent ex]
  (logging/warn ["Agent error" (thrown/stringify _agent ex)]))

(defn get-or-create-repository-processor 
  "Creates a repository processor (agent) given a (repository) hash with 
  :id property"
  [repository]
  (if-let [id (str (:id repository))]
    (or (@repository-processors-atom id)
        ((swap! repository-processors-atom
                (fn [git-repositories id]
                  (conj git-repositories
                        {id {:id id
                             :initial-properties repository
                             :agent (agent {:repository repository}
                                           :error-handler repository-agent-error-handler)
                             :state-atom (atom {})}}))
                id) id))
    (logging/warn "could not create repositorie-processor" repository)))


;### branches #################################################################
(defn get-git-branches [repository-path]
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

(defn update-or-create-branches [tx repository]
  (let [repository-path (git.repositories/path repository)
        git-branches (get-git-branches repository-path)
        canonic-id (git.repositories/canonic-id repository)]
    (logging/debug update-or-create-branches {:repository-path repository-path
                                              :git-branches git-branches
                                              :canonic-id canonic-id})
    (sql.branches/delete-removed tx git-branches canonic-id)
    (let [created (branches/create-new tx git-branches canonic-id repository-path)
          updated (branches/update-outdated tx git-branches canonic-id repository-path)]
      (concat created updated)))) 


;### GIT Stuff ################################################################
(defn update-git-server-info [repository]
  (logging/debug update-git-server-info [repository])
  (let [repository-path (git.repositories/path repository)
        id (git.repositories/canonic-id repository) ]
    (system/exec-with-success-or-throw ["git" "update-server-info"] 
                 {:watchdog (* 10 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})))

(defn send-branch-update-notifications [branches]
  (catcher/wrap-with-log-error
    (logging/debug send-branch-update-notifications [branches])
    (doseq [branch branches]
      (messaging/publish "branch.updated" branch))))

(defn git-update [repository]
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
        ["git" "clone" "--mirror" (:origin_uri repository) dir]
        {:watchdog (* 5 60 1000)}))))

(defn git-fetch-or-initialize [repository]
  (try (catcher/wrap-with-log-error
         (let [repository-path (git.repositories/path repository)] 
           (if (fs/exists? repository-path)
             (git-initialize repository)
             (system/exec-with-success-or-throw 
               ["git" "fetch" (:origin_uri repository) "--force" "--tags" "--prune"  "+*:*"]
               {:watchdog (* 10 60 1000), 
                :dir repository-path, 
                :env {"TERM" "VT-100"}})))
         (catch Exception _
           (git-initialize repository)))))


;### Submit actions through agent #############################################
(defn git-update-is-due? [repository git-repository]
  (when-let [interval-value (:git_update_interval repository)]
    (if-let [git-updated-at (:git_updated_at @(:agent git-repository))]
      (time/after? (time/now) (time/plus git-updated-at (time/seconds interval-value)))
      true)))

(defn git-fetch-is-due? [repository git-repository]
  (when-let [interval-value (:git_fetch_and_update_interval repository)]
    (if-let [git-fetched-at (:git_fetched_at @(:agent git-repository))]
      (time/after? (time/now) (time/plus git-fetched-at (time/seconds interval-value)))
      true)))

(defn submit-git-update [repository git-repository]
  (logging/debug submit-git-update [repository git-repository])
  (send-off (:agent git-repository)
            (fn [state repository git-repository] 
              ; possibly skip overflow of the queue 
              (if (git-update-is-due? repository git-repository)
                (do (git-update repository)
                    (conj state {:git_updated_at (time/now)}))
                state))
            repository git-repository))

(defn submit-git-fetch-and-update [repository git-repository]
  (logging/debug submit-git-fetch-and-update [repository git-repository])
  (send-off (:agent git-repository)
            (fn [state repository git-repository] 
              ; possibly skip overflow of the queue 
              (if (git-fetch-is-due? repository git-repository)
                (do (git-fetch-or-initialize repository)
                  (git-update repository)
                  (conj state {:git_fetched_at (time/now)}))
                state))
            repository git-repository))

(defn submit-git-initialize [repository git-repository]
  (send-off (:agent git-repository)
            (fn [state repository] 
              (git-initialize repository)
              (git-fetch-or-initialize repository)
              (git-update repository)
              (conj state {:git-initialized-at (time/now)}))
            repository))


;### update-repositories ######################################################
(defn update-repositories []
  ;(logging/debug update-repositories)
  (doseq [repository (jdbc/query (rdbms/get-ds) ["SELECT * from repositories"])]
    (let [repository-processor (get-or-create-repository-processor repository)]
      ;(logging/debug "check for git-fetch-is-due?" {:repository repository :repository-processor repository-processor})
      (if (git-fetch-is-due? repository repository-processor)
        (submit-git-fetch-and-update repository repository-processor)
        (when (git-update-is-due? repository repository-processor)
          (submit-git-update repository repository-processor))))))


(daemon/define "update-repositories" 
  start-update-repositories
  stop-update-repositories
  1
  (update-repositories))


;### update-repositories ######################################################

(defn initialize []
  (start-update-repositories)
  )


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

