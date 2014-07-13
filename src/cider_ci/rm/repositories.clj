; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.rm.repositories
  (:require 
    [cider-ci.rm.branches :as branches] 
    [cider-ci.rm.git.repositories :as git.repositories] 
    [cider-ci.rm.sql.branches :as sql.branches] 
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.system :as system]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)



(declare 
  update-service-start
  update-service-stop
  )


;### config and initialization ################################################
(defonce conf (atom {}))

; TODO problematic when called multiple times; wrap in agent? 
(defn initialize [new-conf]
  (logging/info initialize [new-conf])
  (reset! conf new-conf)
  (git.repositories/initialize (:repositories @conf))
  (update-service-stop)
  (update-service-start))




;### repositories processors ##################################################
(defonce repository-processors-atom (atom {}))
(defn repository-agent-error-handler [_agent ex]
  (logging/warn ["Agent error" (exception/stringify _agent ex)]))

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
  (let [res (system/exec
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
  ;(get-git-branches "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")

(defn update-or-create-branches [ds repository]
  (logging/debug update-or-create-branches ["ds" repository])
  (let [repository-path (git.repositories/path repository)
        git-branches (get-git-branches repository-path)
        canonic-id (git.repositories/canonic-id repository)]
    (logging/debug update-or-create-branches {:repository-path repository-path
                                              :git-branches git-branches
                                              :canonic-id canonic-id})
    (sql.branches/delete-removed ds git-branches canonic-id)
    (let [created (branches/create-new ds git-branches canonic-id repository-path)
          updated (branches/update-outdated ds git-branches canonic-id repository-path)]
      (concat created updated)))) 
  ;(hooke/add-hook #'update-or-create-branches #'util/logit)



;### GIT Stuff ################################################################

(defn update-git-server-info [repository]
  (logging/debug update-git-server-info [repository])
  (let [repository-path (git.repositories/path repository)
        id (git.repositories/canonic-id repository) ]
    (system/exec ["git" "update-server-info"] 
                 {:watchdog (* 10 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})))

;# TODO check sensible name for routing-key (amqp convention?) 
;# TODO check parameters of branches
(defn send-branch-update-notifications [branches]
  (with/logging
    (logging/debug send-branch-update-notifications [branches])
    (doseq [branch branches]
      (messaging/publish-event
        "branch_event_topic"
        "update"
        branch
        ))))

(defn git-update [repository]
  (with/logging
    (logging/debug git-update [repository])
    (let [updated-branches (atom nil)]
      (jdbc/with-db-transaction [tx (:ds @conf)]
        (let [dir (git.repositories/path repository)
              sid (str (git.repositories/canonic-id repository))]
          (update-git-server-info repository)
          (reset! updated-branches (update-or-create-branches tx repository))
          ))
      (send-branch-update-notifications @updated-branches))))

(defn git-initialize [repository]
  (with/logging
    (logging/debug git-initialize [repository])
    (let [dir (git.repositories/path repository)
          sid (str (git.repositories/canonic-id repository))]
      (system/exec ["rm" "-rf" dir])
      (system/exec ["git" "clone" "--mirror" (:origin_uri repository) dir]))))

(defn git-fetch [repository]
  (with/logging
    (logging/debug git-fetch [repository])
    (let [repository-path (git.repositories/path repository)
          id (git.repositories/canonic-id repository)
          repository-file (clojure.java.io/file repository-path) ] 
      (logging/debug {:repository-path repository-path :id id :repository-file repository-file})
      (if (and (.exists repository-file) (.isDirectory repository-file))
        (do (system/exec ["git" "fetch" (:origin_uri repository) "-p" "+refs/heads/*:refs/heads/*"] 
                         {:watchdog (* 10 60 1000), 
                          :dir repository-path, 
                          :env {"TERM" "VT-100"}}))
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
                (do (git-fetch repository)
                  (git-update repository)
                  (conj state {:git_fetched_at (time/now)}))
                state))
            repository git-repository))

(defn submit-git-initialize [repository git-repository]
  (send-off (:agent git-repository)
            (fn [state repository] 
              (git-initialize repository)
              (git-fetch repository)
              (git-update repository)
              (conj state {:git-initialized-at (time/now)}))
            repository))


;### update-repositories ######################################################
(defn update-repositories []
  ;(logging/debug update-repositories)
  (doseq [repository (jdbc/query (:ds @conf) ["SELECT * from repositories"])]
    (let [repository-processor (get-or-create-repository-processor repository)]
      ;(logging/debug "check for git-fetch-is-due?" {:repository repository :repository-processor repository-processor})
      (if (git-fetch-is-due? repository repository-processor)
        (submit-git-fetch-and-update repository repository-processor)
        (when (git-update-is-due? repository repository-processor)
          (submit-git-update repository repository-processor))))))


(defonce update-service-done-atom (atom true))
(defonce update-service-future-atom (atom nil))

(defn update-service-start []
  (logging/info update-service-start)
  (reset! update-service-done-atom false)
  (reset! update-service-future-atom 
          (future 
            (loop []
              (Thread/sleep 1000)
              (when-not @update-service-done-atom
                (with/suppress-and-log-warn
                  (update-repositories))
                (recur))))))

(defn update-service-stop []
  (logging/info update-service-stop)
  (reset! update-service-done-atom true)
  (when-let [update-service-future @update-service-future-atom]
    (deref update-service-future)
    (reset! update-service-future-atom nil)))


