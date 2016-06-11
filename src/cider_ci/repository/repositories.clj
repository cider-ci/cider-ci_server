; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories
  (:require

    [cider-ci.repository.repositories.fetch-and-update :as fetch-and-update]
    [cider-ci.repository.branches :as branches]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.branches :as sql.branches]

    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.duration :as duration]
    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]

    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [me.raynes.fs :as fs]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
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
                             :agent (agent {:repository repository
                                            :fetch-and-update-is-queued (atom false) }
                                           :error-handler repository-agent-error-handler)}}))
                id) id))
    (logging/warn "could not create repositorie-processor" repository)))


;### Submit actions through agent #############################################

(defn- git-fetch-and-update-interval [repository]
  (time/seconds
    (snatch
      {:return-expr 60}
      (duration/parse-string-to-seconds
        (:git_fetch_and_update_interval repository)))))

(defn- git-fetch-is-due? [repository git-repository]
  (if-let [git-fetched-at (:git-fetched-at @(:agent git-repository))]
    (time/after?
      (time/now)
      (time/plus git-fetched-at (git-fetch-and-update-interval repository)))
    true))

(defn- git-fetch-and-update-fn [state repository]
  (catcher/snatch {}
    (fetch-and-update/git-fetch-or-initialize repository)
    (fetch-and-update/git-update repository))
  (conj state  {:git-fetched-at (time/now)}))

(defn- submit-git-fetch-and-update [repository git-repository]
  (logging/debug submit-git-fetch-and-update [repository git-repository])
  (let [repo-agent (:agent git-repository)]
    (logging/debug 'submit-git-fetch-and-update "evaluating submit")
    (when-not (-> @repo-agent :fetch-and-update-is-queued deref)
      (logging/debug 'submit-git-fetch-and-update "sending-off git-fetch-and-updat")
      (swap! (-> @repo-agent :fetch-and-update-is-queued) (fn [_] true))
      (send-off repo-agent
                (fn [state repository git-repository]
                  (logging/debug 'submit-git-fetch-and-update "executing git-fetch-and-updat")
                  (swap! (-> state :fetch-and-update-is-queued) (fn [_] false))
                  (git-fetch-and-update-fn state repository))
                repository git-repository))))

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
        ))))

(defdaemon "update-repositories" 1 (update-repositories))


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

