; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.fetch-and-update.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:import
    [java.util.concurrent Executors ExecutorService Callable])
  (:require
    [cider-ci.repository.fetch-and-update.fetch :as fetch]
    [cider-ci.repository.fetch-and-update.scheduler :as scheduler]
    [cider-ci.repository.fetch-and-update.shared :as shared :refer [db-get-fetch-and-update db-update-fetch-and-update]]
    [cider-ci.repository.branch-updates.core :as branch-updates]
    [cider-ci.repository.shared :refer :all]
    [cider-ci.repository.state :as state]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [clj-time.core :as time])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]))

(defonce fetch-and-update-pool (atom nil))

(defn- ready-to-be-submited? [repository]
  (contains?
    #{nil "error" "ok" "initializing"}
    (-> repository :id db-get-fetch-and-update :state)))

(defn catch-fetch-and-update-exception [e repository]
  (db-update-fetch-and-update
    (:id repository)
    #(assoc %
       :last_error_at (time/now)
       :last_error (str e)
       :state "error")))

(defn execute-fetch-and-update [repository]
  (let [id (:id repository)]
    (locking (str "fetch-and-update-lock_" id)
      (catcher/snatch
        {:return-fn (fn [e] (catch-fetch-and-update-exception e repository))}
        (let [path (repository-fs-path repository)]
          (fetch/fetch repository path)
          (branch-updates/update repository))))))

(defn- submit-pending-repositories []
  (doseq [repository (map second (:repositories (state/get-db)))]
    (let [id (:id repository)]
      (when (and (ready-to-be-submited? repository)
                 (-> repository :fetch-and-update :pending?))
        (db-update-fetch-and-update id #(assoc % :pending? false
                                               :state "waiting"))
        (let [do-fetch-and-update (fn [] (execute-fetch-and-update repository))]
          (.submit @fetch-and-update-pool (cast Callable do-fetch-and-update)))))))

;(defdaemon "submit-pending-repositories" 1 (submit-pending-repositories))

(defn start-submit-pending-repositories []
  (state/watch-db
    :submit-to-fetch
    (fn [_ _ old-state new-state]
      (when (not= old-state new-state)
        (submit-pending-repositories)))))

;##############################################################################

(defn- initialize-fetch-and-update-pool []
  (let [fetch-and-update-pool-size (or (-> (get-config) :max_concurrent_fetch_and_updates)
                                       (.availableProcessors (Runtime/getRuntime)))]
    (reset! fetch-and-update-pool
            (Executors/newFixedThreadPool fetch-and-update-pool-size))))

;##############################################################################

(def fetch-and-update shared/fetch-and-update)

(defn initialize []
  (initialize-fetch-and-update-pool)
  (start-submit-pending-repositories)
  (submit-pending-repositories)
  (scheduler/initialize))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
