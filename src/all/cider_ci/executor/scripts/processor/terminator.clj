; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.processor.terminator
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.scripts.exec :as exec]
    [cider-ci.executor.trials.helper :as trials]
    [cider-ci.executor.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.map :refer [convert-to-array]]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


;### set terminate flag #######################################################

(defn set-terminate-flag [script-atom]
  (logging/debug 'set-terminate-flag {:script-atom script-atom})
  (swap! script-atom  #(assoc % :terminate true )))

;### terminate when conditions fullfiled ######################################

(defn- terminate-when-fulfilled? [params script-atom trial]
  (catcher/with-logging {}
    (let [script-key (:script_key params)
          script (trials/get-script-by-script-key script-key trial)
          state (:state script)]
      (some #{state} (:states params)))))

(defn- terminate-when-all-fulfilled? [script-atom trial]
  (catcher/with-logging {}
    (boolean
      (when-let [terminators (not-empty (:terminate_when @script-atom))]
        (every?  #(terminate-when-fulfilled? % script-atom trial)
                (-> terminators convert-to-array))))))

(defn- log-seq [msg seq]
  (logging/debug msg {:seq seq})
  (doall seq))

(defn set-to-terminate-when-fulfilled [trial]
  (catcher/with-logging {}
    (->> (trials/get-scripts-atoms trial)
         ;(log-seq 'script-atoms)
         (filter #(-> % deref executing?))
         ;(log-seq 'script-atoms-executing)
         (filter #(terminate-when-all-fulfilled? % trial))
         ;(log-seq 'script-atoms-fulfilled)
         (map set-terminate-flag)
         doall)))


;### abort ####################################################################

(defn- set-to-terminate-when-executing [trial]
  (->> (trials/get-scripts-atoms trial)
       (filter #(-> % deref executing?))
       (map set-terminate-flag)
       doall))

(defn abort [trial] (set-to-terminate-when-executing trial))

;### Debug ####################################################################
(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
