(ns cider-ci.server.state.users
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.state.db :as db]
    [cider-ci.server.state.shared :refer [update-rows-in-db]]

    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.row-events :as row-events]

    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    ))

(defn- update-users []
  (->> ["SELECT * from users"]
       (jdbc/query (rdbms/get-ds))
       (map (fn [repo] [(-> repo :id str) repo]))
       (into {})
       (swap! db/db update-rows-in-db :users {})))

(def ^:private last-processed-user-event (atom nil))

(defdaemon "update-users" 1
  (row-events/process "user_events" last-processed-user-event
                      (fn [_] (update-users))))

(defn initialize []
  (update-users)
  (start-update-users))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.row-events)
;(debug/debug-ns *ns*)
