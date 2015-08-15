; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.dispatcher.sync.sync-trials
  (:require
    [cider-ci.dispatcher.dispatch.build-data :as build-data]
    [cider-ci.dispatcher.dispatch.next-trial]
    [cider-ci.dispatcher.executor :as executor-entity]
    [cider-ci.dispatcher.sync.ping :as ping]
    [cider-ci.dispatcher.sync.update-executor :as update-executor]
    [cider-ci.dispatcher.trial :as trial-entity]
    [cider-ci.dispatcher.trial :as trial-utils]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [honeysql.format :as sql-format]
    [honeysql.helpers :as sql-helpers]
    [honeysql.types :as sql-types]
    ))


;### next trials to be processed ##############################################

(defn- next-trial-query-part-for-executor [params]
  )

(defn- next-trial-to-be-dispatched-query-repositories-part [params]
  (when-let [accepted-repositories (-> params :accepted_repositories seq)]
    [:= :repositories.git_url
     (sql-types/call :ANY (sql-types/call :cast (honeysql.types/array accepted-repositories)
                                          (keyword "varchar[]")))]))

(defn- next-trial-to-be-dispatched-query-traits-part [params]
  [(keyword "<@") :tasks.traits
   (sql-types/call :cast (honeysql.types/array (-> params :traits))
                   (keyword "varchar[]")
                   )])

(defn- next-trial-to-be-dispatched-query [params]
  (->  cider-ci.dispatcher.dispatch.next-trial/next-trial-to-be-dispatched-base-query
      (sql-helpers/merge-where (next-trial-query-part-for-executor params))
      (sql-helpers/merge-where (next-trial-to-be-dispatched-query-traits-part params))
      (sql-helpers/merge-where (next-trial-to-be-dispatched-query-repositories-part params))
      sql-format/format))

(defn- get-and-set-next-trial-to-be-dispatched [tx executor params]
  (let [query (next-trial-to-be-dispatched-query params)]
    (when-let  [trial (-> query (#(jdbc/query tx %)) first)]
      (trial-utils/wrap-trial-with-issue-and-throw-again
        trial  "Error during dispatch"
        (jdbc/update! tx :trials
                      {:state "dispatching" :executor_id (:id executor)}
                      ["id = ?" (:id trial)]))
      (first (jdbc/query tx ["SELECT * FROM trials WHERE id = ?" (:id trial)])))))

(defn- get-trials-to-be-dispatched [executor data]
  (let [available-load (-> data :available_load)]
    (if (or (>= 0 available-load) (not (clojure.string/blank? (:base_url executor))))
      []
      (jdbc/with-db-transaction [tx (rdbms/get-ds)]
        (loop [trials []]
          (if-let [trial (get-and-set-next-trial-to-be-dispatched tx executor data)]
            (let [trials (conj trials trial)]
              (if (< (count trials) available-load)
                (recur trials)
                trials))
            trials))))))



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
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
