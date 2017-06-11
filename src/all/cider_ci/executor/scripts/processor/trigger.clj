; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.processor.trigger
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.scripts.exec :as exec]
    [cider-ci.executor.scripts.processor.terminator :refer [set-to-terminate-when-fulfilled]]
    [cider-ci.executor.scripts.processor.starter :refer [start-scripts]]
    [cider-ci.executor.scripts.processor.skipper :refer [skip-unsatisfiable-scripts]]
    [cider-ci.executor.trials.helper :as trials]
    [cider-ci.executor.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn trigger [trial msg]
  (catcher/with-logging {} (skip-unsatisfiable-scripts trial))
  (catcher/with-logging {} (set-to-terminate-when-fulfilled trial))
  (catcher/with-logging {} (start-scripts trial))
  trial)

(defn- eval-watch-trigger [trial old-state new-state]
  (future
    (catcher/with-logging {}
      (when (not= (:state old-state) (:state new-state))
        (trigger trial (str (:name old-state) " : "
                            (:state old-state) " -> " (:state new-state)))))))


(defn add-watchers [trial]
  (doseq [script-atom (trials/get-scripts-atoms trial)]
    (add-watch script-atom
               :trigger
               (fn [_ script-atom old-state new-state]
                 (eval-watch-trigger trial old-state new-state)))))

(defn remove-watchers [trial]
  (doseq [script-atom (trials/get-scripts-atoms trial)]
    (remove-watch script-atom :trigger)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
