; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.socket.push-db
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.state :as server.state]
    [cider-ci.utils.digest :refer [digest]]
    [cider-ci.server.socket.shared :refer [user-clients* chsk-send!]]
    [cider-ci.utils.daemon :refer [defdaemon]]

    [clojure.data.json :as json]
    [timothypratley.patchin :refer [diff]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


;### push data ################################################################

(defn db-state-filter-repositories [db-state user-client]
  (let [client-path (-> user-client :client-state :current-page :full-path)]
    (logging/debug 'client-path client-path)
    (if (and client-path (re-matches #"\/cider-ci\/repositories\/projects.*" client-path))
      db-state
      (dissoc db-state :repositories))))

(defn db-state-set-user [db-state user-client-id]
  (let [user-id (-> user-client-id (clojure.string/split #"_") first)
        user (get (:users db-state) user-id {})]
    (-> db-state
        (dissoc :users)
        (assoc :user (dissoc user :password_digest)))))

(defn target-remote-state [db-state user-client user-client-id]
  (-> db-state
      (db-state-set-user user-client-id)
      (db-state-filter-repositories user-client)
      json/write-str
      (json/read-str :key-fn keyword)))

(def empty-diff [{}{}])

(defn push-data [user-client target-remote-state]
  (let [target-remote-state-digest (digest target-remote-state)]
    (if-let [current-remote-state (-> user-client :server-state)]
      (let [d (diff current-remote-state target-remote-state)]
        (if (= d empty-diff)
          nil
          {:patch d :digest target-remote-state-digest}))
      {:full target-remote-state :digest target-remote-state-digest})))

(defn push-to-client-swap-fn [user-clients user-client-id push-data* db-state]
  (if-let [user-client (get user-clients user-client-id nil)]
    (let [target-remote-state (target-remote-state db-state user-client user-client-id)]
      (reset! push-data* (push-data user-client target-remote-state))
      (assoc-in user-clients [user-client-id :server-state] target-remote-state))
    ; nothing known about this user-client-id:
    (do (reset! push-data* nil)
        user-clients)))

(defn push-to-client
  ([user-client-id]
   (push-to-client user-client-id (server.state/get-db)))
  ([user-client-id db-state]
   (let [push-data* (atom nil)]
     (swap! user-clients* push-to-client-swap-fn user-client-id push-data* db-state)
     (logging/debug '@push-data* @push-data*)
     (when-let [push-data @push-data*]
       (chsk-send! user-client-id
                   [(keyword "cider-ci" "state-db")
                    push-data])))))


;### push on changed db-state #################################################

(def db-push-pending? (atom false))

(defn initialize-watch-state-db []
  (server.state/watch-db
    :send-update-to-all-user-clients
    (fn [_ _ old_state new_state]
      (when (not= old_state new_state)
        (reset! db-push-pending? true)))))

(defn- push-to-all-user-clients []
  (doseq [[user-client-id _] @user-clients*]
    (push-to-client user-client-id)))

(defdaemon "push-to-all-user-clients-loop" 0.25
  (when @db-push-pending?
    (reset! db-push-pending? false)
    (push-to-all-user-clients)))


;##############################################################################

(defn initialize []
  (initialize-watch-state-db)
  (start-push-to-all-user-clients-loop))


