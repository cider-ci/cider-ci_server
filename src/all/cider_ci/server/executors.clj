(ns cider-ci.server.executors
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.executors.state :as state]
    [cider-ci.server.executors.web :as web]
    [cider-ci.server.executors.token :as token]

    [clj-time.core :as time]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(def routes web/routes)
(def executors* state/db*)
(defn initialize [] (state/initialize))

(defn update-last-sync-at [executor-id]
  (swap! executors*
         (fn [exs id]
           (if-let [executor (get exs id  nil)]
             (assoc-in exs [id :last_sync_at] (time/now))
             exs))
         (keyword executor-id)))

(defn find-executor-by-token [token]
  (let [hash (token/hash token)]
    (->> @executors*
         (map (fn [[_ e]] e))
         (filter #(= hash (:token_hash %)))
         first)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

