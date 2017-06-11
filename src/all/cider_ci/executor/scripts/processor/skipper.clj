; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.processor.skipper
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.scripts.exec :as exec]
    [cider-ci.executor.trials.helper :refer [get-script-by-script-key get-scripts-atoms]]
    [cider-ci.executor.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.map :refer [convert-to-array]]
    [cider-ci.executor.scripts.processor.starter :refer [amend-with-start-when-defaults start-when-fulfilled?]]
    [clj-time.core :as time]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(def this-ns *ns*)

(defn- debug-pipe [msg x]
  (logging/debug this-ns msg x)
  x)

(defn- dependency-finished? [params script-atom trial]
  (boolean
    (when-let [depend-on-script (get-script-by-script-key (:script_key params) trial)]
               (finished? depend-on-script))))

(defn- unsatisfiable? [& args]
  (not (apply start-when-fulfilled? args)))

(defn- any-unsatisfiable? [script-atom trial]
  (boolean
    (when-let [start-when-conditions (:start_when @script-atom)]
      (->> start-when-conditions
           (debug-pipe ['any-unsatisfiable? 'start-when-conditions])
           convert-to-array
           (debug-pipe ['any-unsatisfiable? 'array])
           (map amend-with-start-when-defaults)
           (debug-pipe ['any-unsatisfiable? 'with-defaults])
           (filter #(dependency-finished? % script-atom trial))
           (debug-pipe ['any-unsatisfiable? 'dependency-finished])
           (filter #(unsatisfiable? % script-atom trial))
           (debug-pipe ['any-unsatisfiable? 'not-fulfilled?])
           empty?
           not))))

(defn- unsatisfiable-scripts [trial]
  (->> (get-scripts-atoms trial)
       (debug-pipe ['unsatisfiable-scripts 'script-atoms])
       (filter #(-> % deref pending?))
       (debug-pipe ['unsatisfiable-scripts 'pending-scripts-atoms])
       (filter #(any-unsatisfiable? % trial))
       (debug-pipe ['unsatisfiable-scripts 'result])
       doall))

(defn skip-script [script-atom by-reason]
  (swap! script-atom
         (fn [script]
           (if (= "pending" (:state script))
             (assoc script
                    :state "skipped"
                    :skipped_at (time/now)
                    :skipped_by by-reason)
             script))))

(defn skip-unsatisfiable-scripts [trial]
  (->> (unsatisfiable-scripts trial)
       (map #(skip-script % "unsatisfiable check"))
       doall)
  trial)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
