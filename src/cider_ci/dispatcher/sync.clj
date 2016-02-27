; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.sync
  (:refer-clojure :exclude [sync])
  (:require
    [cider-ci.dispatcher.executor :as executor-entity]
    [cider-ci.dispatcher.sync.ping :as ping]
    [cider-ci.dispatcher.sync.sync-trials :as sync-trials]
    [cider-ci.dispatcher.sync.update-executor :as update-executor]
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.set :refer [difference]]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn in-progress-trials-of-executor [executor]
  "Trials believed being in progress on executor
   and which have not been updated within the last minute."
  (jdbc/query (rdbms/get-ds)
              ["SELECT trials.id, state, started_at, finished_at FROM trials
               JOIN executors ON trials.executor_id = executors.id
               WHERE executors.id = ?
               AND state IN ('dispatching', 'executing', 'aborting')
               AND (trials.updated_at < (now() - interval '1 Minutes'))
               " (:id executor)]))

(defn fail-trials-lost-on-executor [executor reported-executor-trials]
  (let [known-trial-ids-by-executor (map :trial_id reported-executor-trials)
        in-progress-trials-of-executor (in-progress-trials-of-executor executor)
        in-progress-trials-of-executor-ids (map #(-> % :id str) in-progress-trials-of-executor)]
    (doseq [lost-trial-id  (difference (set in-progress-trials-of-executor-ids)
                                       (set known-trial-ids-by-executor))]
      (trials/update-trial {:id lost-trial-id
                            :state "aborted"
                            :error (str "This trial was lost on executor " (:name executor))}))))

(defn sync [executor data]
  (catcher/with-logging {}
    (fail-trials-lost-on-executor executor (:trials data))
    (update-executor/update-when-changed executor data)
    (ping/update-last-ping-at executor)
    ;jobs-to-execute (get-jobs-to-execute executor (:body request))]
    {:status 200
     :body (json/write-str (sync-trials/sync-trials executor data))}))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)

