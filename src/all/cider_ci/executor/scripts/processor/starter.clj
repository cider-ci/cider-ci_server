; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.processor.starter
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.scripts.exec :as exec]
    [cider-ci.executor.trials.helper :as trials]
    [cider-ci.executor.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.map :refer [convert-to-array]]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn start-when-fulfilled? [params script-a trial]
  (catcher/with-logging {}
    (let [script-key (:script_key params)
          script (trials/get-script-by-script-key script-key trial)
          state (:state script)]
      (some #{state} (:states params)))))

; TODO remove type
(defn amend-with-start-when-defaults [properties]
  (deep-merge {:type "script"
               :states ["passed"]}
              properties))

(defn- start-when-all-fulfilled? [script-a trial]
  (catcher/with-logging {}
    (every?
      #(start-when-fulfilled? % script-a trial)
      (->> @script-a
          :start_when
          convert-to-array
          (map amend-with-start-when-defaults)))))

(defn- scripts-atoms-to-be-started [trial]
  (catcher/with-logging {}
    (->> (trials/get-scripts-atoms trial)
         (filter #(-> % deref pending? ))
         (filter #(start-when-all-fulfilled? % trial)))))

(defn- exec?
  "Determines if the script should be executed from this thread/scope.
  Sets the state to dispatched and returns true if so.
  Returns false otherwise. "
  [script-atom]
  (let [r (str (.getId (Thread/currentThread)) "_" (rand))
        swap-in-fun (fn [script]
                      (if (= (:state script) "pending"); we will exec if it is still pending
                        (assoc script :state "waiting" :dispatch_thread_sig r)
                        script))]
    (swap! script-atom swap-in-fun)
    (= r (:dispatch_thread_sig @script-atom))))


;####################################################################

(defonce exclusive-resource-agents-atom (atom {}))

(defn create-execlusive-resource-agent [agent-name]
  (agent {:resource agent-name}
         :error-mode :continue))

(defn get-exclusive-resource-agent [agent-name]
  (-> (swap! exclusive-resource-agents-atom
             (fn [exclusive-resource-agents agent-name]
               (if (get exclusive-resource-agents agent-name)
                 exclusive-resource-agents
                 (assoc exclusive-resource-agents
                        agent-name (create-execlusive-resource-agent agent-name))))
             agent-name)
      (get agent-name)))

(defn exec-inside-agent [agent-state script-atom]
  (when (= "waiting" (:state @script-atom))
    (exec/execute script-atom))
  (assoc agent-state
         :last_finished_at (time/now)
         :last_script @script-atom))

(defn dispatch [script-atom]
  (if-let [exclusive-resource (:exclusive_executor_resource @script-atom)]
    (send-off (get-exclusive-resource-agent exclusive-resource)
              exec-inside-agent script-atom)
    (future (exec/execute script-atom))))

;####################################################################

(defn start-scripts [trial]
  (->> (scripts-atoms-to-be-started trial)
       (filter exec?) ; avoid potential race condition here
       (map dispatch)
       doall)
  trial)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
