; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.branch-updates.core
  (:refer-clojure :exclude [str keyword update])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:import
    [java.util.concurrent Executors ExecutorService Callable])
  (:require
    [cider-ci.server.repository.branch-updates.shared :as shared :refer [db-get-branch-updates db-update-branch-updates]]
    [cider-ci.server.repository.branch-updates.update :as update]
    [cider-ci.server.repository.shared :refer :all]
    [cider-ci.server.repository.state :as state]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [clj-time.core :as time])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]))

(defonce branch-updates-pool (atom nil))

(defn- ready-to-be-submited? [repository]
  (contains?
    #{"error" "ok" "initializing"}
    (-> repository :id db-get-branch-updates :state)))

(defn catch-branch-updates-exception [e repository]
  (db-update-branch-updates
    (:id repository)
    #(assoc %
       :last_error_at (time/now)
       :last_error (str e)
       :state "error")))

(defn- execute-update-branches [repository]
  (let [id (:id repository)]
    (locking (str "fetch-and-update-lock_" id)
      (catcher/snatch
        {:return-fn (fn [e] (catch-branch-updates-exception e repository))}
        (update/update repository)))))

(defn- submit-pending-repositories []
  (doseq [repository (map second (:repositories (state/get-db)))]
    (when (and (ready-to-be-submited? repository)
               (-> repository :branch-updates :pending?))
      (db-update-branch-updates (:id repository) #(assoc % :pending? false))
      (let [do-branch-updates (fn [] (execute-update-branches repository))]
        (db-update-branch-updates (:id repository) #(assoc % :state "waiting"))
        (.submit @branch-updates-pool (cast Callable do-branch-updates))))))

;(defdaemon "submit-pending-repositories" 1 (submit-pending-repositories))

(defn start-submit-pending-repositories []
  (state/watch-db
    :submit-to-update
    (fn [_ _ old-state new-state]
      (when (not= old-state new-state)
        (submit-pending-repositories)))))

;##############################################################################

(defn- initialize-branch-updates-pool []
  (let [branch-updates-pool-size (or (-> (get-config) :max_concurrent_fetch_and_updates)
                                       (.availableProcessors (Runtime/getRuntime)))]
    (reset! branch-updates-pool
            (Executors/newFixedThreadPool branch-updates-pool-size))))

;##############################################################################

(defn update [repository]
  (db-update-branch-updates
    (:id repository) #(assoc % :pending? true)))

(defn initialize []
  (initialize-branch-updates-pool)
  (start-submit-pending-repositories)
  (submit-pending-repositories))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
