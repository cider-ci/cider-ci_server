; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.trials
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.accepted-repositories :as accepted-repositories]
    [cider-ci.executor.attachments :as attachments]
    [cider-ci.executor.environment-variables :as environment-variables]
    [cider-ci.executor.git :as git]
    [cider-ci.executor.port-provider :as port-provider]
    [cider-ci.executor.reporter :as reporter]
    [cider-ci.executor.result :as result]
    [cider-ci.executor.scripts :as scripts]
    [cider-ci.executor.scripts.processor]
    [cider-ci.executor.shared :refer :all]
    [cider-ci.executor.trials.helper :refer :all]
    [cider-ci.executor.trials.state :refer [create-trial get-trial]]
    [cider-ci.executor.trials.templates :refer [render-templates render-string-template]]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.system :as system]

    [clj-time.core :as time]
    [clojure.data.json :as json]
    [me.raynes.fs :as clj-fs]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    )
  (:import
    [org.apache.commons.lang3 SystemUtils]
    [java.io File]
    ))


;#### prepare trial ###########################################################

(defn- set-and-send-start-params [trial]
  (let [params-atom (get-params-atom trial)]
    (swap! params-atom (fn [params] (conj params {:state "executing"})))
    (send-patch-via-agent trial (select-keys @params-atom [:state :started_at])))
  trial)

(defn- prepare-scripts [trial]
  (let [params-atom (get-params-atom trial)]
    (doseq [[_ script-atom] (:scripts @params-atom)]
      (scripts/prepare-script script-atom @params-atom)))
  trial)


;#### stuff ###################################################################

(defn- create-and-insert-working-dir [trial]
  "Creates a working dir (populated with the checked out git repo),
  adds the :working_dir key to the params-atom and sets the corresponding
  value. Returns the (modified) trial."
  (let [params-atom (get-params-atom trial)
        working-dir (git/prepare-and-create-working-dir @params-atom)
        private-dir (str working-dir File/separator ".cider-ci_private")]
    (clj-fs/mkdir private-dir)
    (swap! params-atom
           #(assoc %1 :working_dir %2 :private_dir %3)
           working-dir private-dir))
  trial)

(defn- change-owner-of-working-dir [trial]
  (let [working-dir (-> trial get-params-atom deref :working_dir)]
    (cond
      SystemUtils/IS_OS_UNIX (system/exec!
                               ["chown" "-R" (exec-user-name) working-dir])
      SystemUtils/IS_OS_WINDOWS (system/exec!
                                  ["icacls" working-dir "/grant"
                                   (str (exec-user-name) ":(OI)(CI)F")]))
    trial))

(defn- occupy-and-insert-ports [trial]
  "Occupies free ports according to the :ports directive,
  adds the corresponding :ports value to the trials aprams
  and each script and also returns the ports."
  (let [params-atom (get-params-atom trial)
        ports (into {} (map (fn [[port-name port-params]]
                              [port-name (port-provider/occupy-port
                                           (or (:inet_address port-params) "localhost")
                                           (:min port-params)
                                           (:max port-params))])
                            (:ports @params-atom)))]
    (swap! (:params-atom trial)
           (fn [params ports]
             (assoc params :ports ports))
           ports)
    (doseq [script-atom (get-scripts-atoms trial)]
      (swap! script-atom #(conj %1 {:ports %2}) ports))
    ports))

(defn put-attachments [trial]
  (attachments/find-and-upload trial)
  trial)

(defn evaluate-states [states]
  (cond
    (empty? states) "defective"
    (every? #{"passed"} states) "passed"
    (every? #{"aborted"} states) "aborted"
    (some #{"defective"} states) "defective"
    (some #{"failed"} states) "failed"
    :else "defective"))

(defn set-final-state [trial]
  (let [final-state (->> (get-scripts-atoms trial)
                         (map deref)
                         (filter #(-> % :ignore_state not))
                         (map :state)
                         (filter identity)
                         evaluate-states)]
    (swap! (get-params-atom trial)
           #(conj %1 {:state %2, :finished_at (time/now)})
           (name final-state)))
  trial)

(defn set-result [trial]
  (let [params-atom (get-params-atom trial)
        working-dir (get-working-dir trial)]
    (result/try-read-and-merge working-dir params-atom))
  trial)

(defn release-ports [ports]
  (doseq [[_ port] ports]
    (port-provider/release-port port)))

(defn send-final-result [trial]
  (let [params (dissoc @(get-params-atom trial) :scripts)]
    (send-patch-via-agent trial params))
  trial)


;#### execute #################################################################

(defn execute-execption-handler [trial exception]
  (swap! (get-params-atom trial)
         (fn [params exception]
           (conj params
                 {:state "defective",
                  :finished_at (time/now)
                  :error (str (.getMessage exception)
                              " See the executor logs for details."
                              )}))
         exception)
  (send-final-result trial)
  trial)

(defn prepare-scripts-environment-variables [trial]
  (doseq [script-atom (get-scripts-atoms trial)]
    (swap! script-atom #(merge % {:environment_variables
                                 (environment-variables/prepare %)})))
  trial)

(defn template-exclusive-executor-resource-locks [trial]
  (doseq [script-atom (get-scripts-atoms trial)]
    (if-let [exclusive-resource (:exclusive_executor_resource @script-atom)]
      (swap! script-atom #(merge % {:exclusive_executor_resource
                                    (render-string-template
                                      exclusive-resource
                                      (:environment_variables %))}))))
  trial)

(defn execute [params]
  (when-not (-> params :trial_id get-trial)
    (let [trial (create-trial params)]
      (future
        (snatch
          {:level :warn
           :return-fn (fn [e] (execute-execption-handler trial e))}
          (accepted-repositories/assert-satisfied (:git_url params))
          (->> trial
               create-and-insert-working-dir
               prepare-scripts)
          (let [ports (occupy-and-insert-ports trial)]
            (try (->> trial
                      render-templates
                      prepare-scripts-environment-variables
                      template-exclusive-executor-resource-locks
                      change-owner-of-working-dir
                      set-and-send-start-params
                      cider-ci.executor.scripts.processor/process
                      put-attachments
                      set-final-state
                      set-result
                      send-final-result)
                 (finally (release-ports ports)
                          trial)))
          trial)))))


;#### initialize ###############################################################

(defn initialize []
  )


;### Debug ####################################################################
;logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'prepare-scripts-environment-variables)
