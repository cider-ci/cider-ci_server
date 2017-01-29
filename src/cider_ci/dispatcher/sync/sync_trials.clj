; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.dispatcher.sync.sync-trials
  (:require
    [cider-ci.dispatcher.dispatch.build-data :as build-data]
    [cider-ci.dispatcher.dispatch.next-trial :as next-trial]
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.dispatcher.task :as task]

    [cider-ci.utils.rdbms :as rdbms]

    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [honeysql.format :as sql-format]
    [honeysql.helpers :as sql-helpers]
    [honeysql.types :as sql-types]

    [clj-logging-config.log4j :as logging-config]
    ))


;### next trials to be processed ##############################################

(def get-trial-lock (Object.))

(defn- get-and-set-next-trial [executor]
  (when-let
    [trial (locking get-trial-lock
             (jdbc/with-db-transaction [tx (rdbms/get-ds)]
               (when-let [{trial-id :id} (next-trial/next-trial-for-pull tx executor)]
                 (trials/wrap-trial-with-issue-and-throw-again
                   {:id trial-id}  "Error during dispatch"
                   (jdbc/update! tx :trials
                                 {:state "dispatching"
                                  :dispatched_at (time/now)
                                  :executor_id (:id executor)}
                                 ["id = ?" trial-id]))
                 (first (jdbc/query tx ["SELECT * FROM trials WHERE id = ?" trial-id])))))]
    trial))

(defn- get-trials-in-dispatching-mode [executor]
  (->> [(str "SELECT * FROM trials WHERE state = 'dispatching' "
             " AND  executor_id = ? ") (:id executor) ]
       (jdbc/query (rdbms/get-ds))))

(defn- get-pending-trials-to-be-dispatched [executor data]
  (let [available-load (-> data :available_load)]
    (if (>= 0 available-load)
      []
      (loop [trials []]
        (if-let [trial (get-and-set-next-trial executor)]
          (let [trials (conj trials trial)]
            (if (< (count trials) available-load)
              (recur trials)
              trials))
          trials)))))


(defn- get-trials-to-be-dispatched [executor data]
  (concat
    (get-trials-in-dispatching-mode executor)
    (get-pending-trials-to-be-dispatched executor data)))

;### trials beeing currently processed ########################################

(def ^:private processing-trials-query
  (-> (sql-helpers/select :*)
      (sql-helpers/from :trials)
      (sql-helpers/merge-where [:= :executor_id (sql-types/param :executor_id)])
      (sql-helpers/merge-where [:in :state ["dispatching" "executing" "aborting"]])))

(defn- get-trials-being-processed-by-executor [executor]
  (->> (jdbc/query (rdbms/get-ds)
                   (sql-format/format processing-trials-query
                                      {:executor_id (:id executor)}))
       (map #(dissoc % :scripts))))


;##############################################################################

(defn sync-trials [executor data]
  {:trials-being-processed (get-trials-being-processed-by-executor executor)
   :trials-to-be-executed (->> (get-trials-to-be-dispatched executor data)
                         (map #(build-data/build-dispatch-data % executor)))})


;#### debug ###################################################################
;(debug/wrap-with-log-debug #'sync-trials)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
