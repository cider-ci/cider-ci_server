(ns cider-ci.executors.state
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clojure.set :refer [difference]]

    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.row-events :as row-events]
    [cider-ci.utils.state]


    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    ))


(def db* (atom {}))

(defn update-executors []
  (->> ["SELECT * from executors"]
       (jdbc/query (rdbms/get-ds))
       (map #(assoc % :type :executor))
       (map (fn [u] [(-> u :id keyword) u]))
       (into {})
       (swap! db* cider-ci.utils.state/update-rows)))

(def ^:private last-processed-executor-event (atom nil))

(defdaemon "update-executors" 1
  (row-events/process "executor_events" last-processed-executor-event
                      (fn [_] (update-executors))))

(defn initialize []
  (update-executors)
  (start-update-executors))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.row-events)
;(debug/debug-ns *ns*)

