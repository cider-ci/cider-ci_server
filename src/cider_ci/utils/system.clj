; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.system
  (:require
    [cider-ci.utils.duration :as duration]
    [logbug.debug :as debug]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [clj-time.core :as time]
    )
  (:import
    [java.io File]
    [org.apache.commons.exec ExecuteWatchdog]
    [org.apache.commons.lang3 SystemUtils]
    ))


;### helpers ###################################################################


(defn create-watchdog
  ([]
   (create-watchdog (ExecuteWatchdog/INFINITE_TIMEOUT)))
  ([timeout-ms]
   (ExecuteWatchdog. timeout-ms )))

(defn normalized-timeout-ms [opts]
  (let [timeout (or (:timeout opts)
                    (:watchdog opts))]
    (cond
      (string? timeout)  (-> timeout
                             duration/parse-string-to-seconds
                             (* 1000.0))
      (integer? timeout) (* timeout 1.0)
      :else (* 10 1000.0))))


;### NEW #######################################################################

(defn cancle-async-exec [wrapped-exec]
  (when (not (realized? (:exec @wrapped-exec)))
    (swap! wrapped-exec (fn [r]
                          (assoc r :exception
                                 (ex-info (str "Execution `"
                                               (clojure.string/join " " (:command r))
                                               "` has been canceled!") {}))))
    (.destroyProcess (-> @wrapped-exec :opts :watchdog))))

(defn async-exec
  ([command]
   (async-exec command {}))
  ([command opts]
   (let [timeout-ms (normalized-timeout-ms opts)
         expires-at (time/plus (time/now) (time/millis timeout-ms))
         options (conj opts {:watchdog (create-watchdog)})
         wrapped-exec (atom {:command command
                             :opts options
                             :exec (commons-exec/sh command options)})]
     (future (while (and (not (realized? (-> @wrapped-exec :exec)))
                         (time/before? (time/now) expires-at))
               (Thread/sleep 50))
             (when (not (realized? (:exec @wrapped-exec)))
               (swap! wrapped-exec (fn [r]
                                     (assoc r :exception
                                            (ex-info (str "Execution `"
                                                          (clojure.string/join " " (:command r))
                                                          "` timed out!") r))))
               (.destroyProcess (-> @wrapped-exec :opts :watchdog))))
     wrapped-exec)))

(defn exec!
  ([command]
   (exec! command {}))
  ([command opts]
   (let [wrapped-exec (async-exec command opts)
         realized-exec (-> wrapped-exec deref :exec deref)]
     (when-let [ex (-> wrapped-exec deref :exception)] (throw ex))
     (when-not (= 0 (:exit realized-exec))
       (throw  (ex-info (str "Execution `"
                             (clojure.string/join " " (-> @wrapped-exec :command))
                             "` exited with non zero status!")
                        @wrapped-exec)))
     realized-exec)))


;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
