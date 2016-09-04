; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.state
  (:require

    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.self :as self]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.config :refer [get-config]]

    [clojure.set :refer [difference]]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [honeysql.helpers :refer [group]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(def db (atom {:release (self/release)
               :repositories {}
               :users {}}))

(defn- remove-rows [now-rows update-rows]
  (apply dissoc now-rows (difference (-> now-rows keys set) (-> update-rows keys set))))

(defn update-rows-in-db [db-state sub-key rows]
  (assoc db-state sub-key
         (as-> db-state db-rows
           (get db-rows sub-key)
           (remove-rows db-rows rows)
           (map (fn [[k m]] [k (merge (get db-rows k {}) m )]) rows)
           (sort db-rows)
           (into {} db-rows))))

;### config ###################################################################

(defn update-config []
  (let [config (-> (get-config)
                   (select-keys [:server_base_url]))]
    (swap! db assoc-in [:config] config)))

;### repositories #############################################################

(defn update-repositories []
  (->> ["SELECT * from repositories"]
       (jdbc/query (rdbms/get-ds))
       (map (fn [repo] [(-> repo :id str) repo]))
       (into {})
       (swap! db update-rows-in-db :repositories)))

(defdaemon "update-repositories" 1 (update-repositories))

(defn update-repo-branches [repo-id]
  (let [params (->> (-> (sql/select [:%count.* :branches_count]
                                    [:%max.commits.committer_date :last_commited_at])
                        (sql/from :repositories)
                        (sql/merge-where [:= :repositories.id repo-id])
                        (sql/merge-join :branches [:= :repositories.id :branches.repository_id])
                        (sql/merge-join :commits [:= :branches.current_commit_id :commits.id])
                        (group :repositories.id)
                        sql/format)
                    (jdbc/query (rdbms/get-ds))
                    first)]
    (when params
      (swap! db update-in [:repositories (str repo-id)] #(deep-merge % params)))))

;(update-repo-branches "f22d5ad8-604d-5c03-8773-3e0cce1d13d8")


;### users #############################################################

(defn update-users []
  (->> ["SELECT * from users"]
       (jdbc/query (rdbms/get-ds))
       (map (fn [repo] [(-> repo :id str) repo]))
       (into {})
       (swap! db update-rows-in-db :users)))

(defdaemon "update-users" 10 (update-users))


;### debug change states ######################################################

(add-watch db :debug-watch
           (fn [_ _ before after]
             (logging/debug 'DB-CHANGE {:before (:repositories before) :after (:repositories after)})
             ))


;### initialize ###############################################################

(defn initialize []
  (update-repositories)
  (start-update-repositories)
  (update-users)
  (start-update-users)
  (update-config))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
