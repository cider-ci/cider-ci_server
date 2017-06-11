(ns cider-ci.server.state.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require

    [clojure.set :refer [difference]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]

    ))

(defn- remove-rows [now-rows update-rows]
  (apply dissoc now-rows (difference (-> now-rows keys set) (-> update-rows keys set))))

(defn update-rows-in-db [db-state sub-key default-row rows]
  (assoc db-state sub-key
         (as-> db-state db-rows
           (get db-rows sub-key)
           (remove-rows db-rows rows)
           (map (fn [[k row]] [k (merge (get db-rows k default-row) row)]) rows)
           (sort db-rows)
           (into {} db-rows))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
