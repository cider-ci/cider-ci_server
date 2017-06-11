; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.trials.helper
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.reporter :as reporter]
    [cider-ci.utils.http :refer [build-server-url]]

    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    ))

;TODO unify this with constants
(def ^:private terminal-states #{"passed" "failed" "aborted" "skipped"})

(defn get-params-atom [trial]
  (catcher/with-logging {} (:params-atom trial)))

(defn get-id [trial]
  (catcher/with-logging {} (:trial_id @(get-params-atom trial))))

(defn get-working-dir [trial]
  (catcher/with-logging {} (:working_dir @(get-params-atom trial))))

(defn get-scripts-atoms [trial]
  (catcher/with-logging {} (->> @(get-params-atom trial)
                                    :scripts
                                    (map second))))

(defn get-script-by-name [script-name trial]
  (catcher/with-logging {}
    (->> (get-scripts-atoms trial)
         (map deref)
         (filter #(= script-name (:name %)))
         first )))

(defn get-script-by-script-key [script-key trial]
  (catcher/with-logging {}
    (->> (get-scripts-atoms trial)
         (map deref)
         (filter #(= script-key (:key %)))
         first)))

(defn scripts-done? [trial]
  (catcher/with-logging {} (->> trial
                       get-scripts-atoms
                       (map deref)
                       (map :state)
                       (every? terminal-states))))

(defn send-patch-via-agent [trial data]
  (let [trial-params (-> (get-params-atom trial) deref)

        report-agent (:report-agent trial)
        body (json/write-str data)
        params {:body body
                :content-type "application/json"
                :headers {:trial-token (:token trial-params)}
                }
        url (build-server-url (trial-params :patch_path))
        fun (fn [agent-state]
              (try
                (catcher/with-logging {}
                  (let [res (reporter/send-request-with-retries :patch url params)]
                    (conj agent-state res)))
                (catch Throwable e
                  (conj agent-state {:exception e}))))]
    (logging/debug "sending-report-off" {:url url :params params})
    (send-off report-agent fun)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'send-patch-via-agent)
;(debug/unwrap-with-log-debug #'send-patch-via-agent)
