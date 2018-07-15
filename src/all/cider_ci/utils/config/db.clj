; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.config.db
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [str keyword deep-merge presence]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.duration :refer [parse-string-to-seconds]]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.row-events :as row-events]

    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :refer [snatch with-logging]]
    ))


(defn merge-col [config [ks data]]
  (if (empty? ks)
    (deep-merge config data)
    (update-in config ks
               (fn [m] (deep-merge m data)))))

;(merge-col {:x 5} [[] {:z 42}])

(defn merge-cols [config cols]
  (reduce merge-col config cols))

;(merge-cols {:x 5} [[[] {:z 42}]])

(defn assign-rows [row cols]
  (->> cols
       (map (fn [[rk ks]]
              [ks (get row rk nil)]))))

(defn read-and-merge-db-table [config [table cols]]
  (when-let [ds (rdbms/get-ds)]
    (when-let [row (->> (str "SELECT * FROM " table)
                        (jdbc/query ds) first)]
      (merge-cols config (assign-rows row cols)))))

(defn read-and-merge [config db-tables-conf]
  (reduce read-and-merge-db-table config (into [] db-tables-conf)))



;;; watch and reload ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def config-tables*
  "The tables we watch for changes via events."
  (atom #{}))

(def update-fn* (atom nil))

(def ^:private last-processed-event (atom nil))

(defn eval-row-event [event]
  (when (@config-tables* (:table_name event))
    (@update-fn*)))

(defn process-event-rows []
  (row-events/process
    "events" last-processed-event
    eval-row-event))

(defdaemon "process-event-rows" 0.5
  (process-event-rows))


;;; initialize ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize [opts update-fn]
  (when-not (empty? (->> opts :db-tables keys))
    (reset! config-tables* (->> opts :db-tables
                                keys (map str) set))
    (reset! update-fn* update-fn)
    (start-process-event-rows)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
