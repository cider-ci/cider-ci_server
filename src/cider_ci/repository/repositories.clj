; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories
  (:require
    [cider-ci.repository.repositories.fetch-and-update :as fetch-and-update]
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


;### repositories processors ##################################################
(defonce repository-processors-atom (atom {}))
(defn- repository-agent-error-handler [_agent ex]
  (logging/warn ["Agent error" _agent (thrown/stringify ex)]))

(defn- get-or-create-repository-processor
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


;### Submit actions through agent #############################################
(defn- git-update-is-due? [repository git-repository]
  (when-let [interval-value (:git_update_interval repository)]
    (if-let [git-updated-at (:git_updated_at @(:agent git-repository))]
      (time/after? (time/now) (time/plus git-updated-at (time/seconds interval-value)))
      true)))

(defn- git-fetch-is-due? [repository git-repository]
  (when-let [interval-value (:git_fetch_and_update_interval repository)]
    (if-let [git-fetched-at (:git_fetched_at @(:agent git-repository))]
      (time/after? (time/now) (time/plus git-fetched-at (time/seconds interval-value)))
      true)))

(defn- submit-git-update [repository git-repository]
  (logging/debug submit-git-update [repository git-repository])
  (send-off (:agent git-repository)
            (fn [state repository git-repository]
              ; possibly skip overflow of the queue
              (if (git-update-is-due? repository git-repository)
                (do (fetch-and-update/git-update repository)
                    (conj state {:git_updated_at (time/now)}))
                state))
            repository git-repository))

(defn- git-fetch-and-update-fn [state repository]
  (catcher/wrap-with-suppress-and-log-warn
    (fetch-and-update/git-fetch-or-initialize repository)
    (fetch-and-update/git-update repository))
  (conj state  {:git-fetched-at (time/now)}))

(defn- submit-git-fetch-and-update [repository git-repository]
  (logging/debug submit-git-fetch-and-update [repository git-repository])
  (let [repo-agent (:agent git-repository)]
    (send-off repo-agent
              (fn [state repository git-repository]
                (if-let [git-fetched-at (:git-fetched-at state)]
                  ; skip if it has been fetched in the last 500ms
                  (if (time/after? (time/now) (time/plus git-fetched-at (time/millis 500)))
                    (git-fetch-and-update-fn state repository)
                    state)
                  (git-fetch-and-update-fn state repository)))
              repository git-repository)))

(defn- submit-git-initialize [repository git-repository]
  (send-off (:agent git-repository)
            (fn [state repository]
              (fetch-and-update/git-initialize repository)
              (fetch-and-update/git-fetch-or-initialize repository)
              (fetch-and-update/git-update repository)
              (conj state {:git-initialized-at (time/now)}))
            repository))


;### update-repositories ######################################################
(defn- update-repositories []
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


;### update repository ########################################################

(defn update-repository [repository]
  (let [repository-processor (get-or-create-repository-processor repository)]
    (submit-git-fetch-and-update repository repository-processor)
    ))

;### initialize ###############################################################

(defn initialize []
  (start-update-repositories)
  )


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

