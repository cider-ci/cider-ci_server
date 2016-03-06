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

(defn create-watchdog []
  (ExecuteWatchdog. (ExecuteWatchdog/INFINITE_TIMEOUT) ))


[(time/plus (time/now) (time/seconds 0.0))
 (time/plus (time/now) (time/millis 500.0))
 ]

(defn normalized-timeout-ms [opts]
  (let [timeout (or (:timeout opts)
                    (:watchdog opts))]
    (cond
      (string? timeout)  (-> timeout
                             duration/parse-string-to-seconds
                             (* 1000.0))
      (integer? timeout) (* timeout 1.0)
      :else 1000.0)))

(defn exec
  ([command]
   (exec command {}))
  ([command opts]
   (when (:watchdog opts)
     (logging/warn (str "`" (clojure.string/join " " command) "`  "
                        "The :watchdog option is deprecated, use :timeout instead."
                        {:opts opts})))
   (let [timeout-ms (normalized-timeout-ms opts)
         expires-at (time/plus (time/now) (time/millis (+ timeout-ms 100)))
         watchdog (create-watchdog)
         options (conj opts {:watchdog watchdog})
         exec-future (commons-exec/sh command options) ]
     (while (and (not (realized? exec-future))
                 (time/before? (time/now) expires-at))
       (Thread/sleep 50))
     (if (realized? exec-future)
       @exec-future
       (do
         (.destroyProcess watchdog)
         (throw (ex-info (str "Execution `"
                              (clojure.string/join " " command)
                              "` expired.") opts)))))))


(defn exec-with-success-or-throw [& args]
  (let [res (apply exec args)]
    (if (= 0 (:exit res))
      res
      (throw (ex-info
               (str "Execution `"
                    (clojure.string/join " " (first args))
                    "` exited with non zero exit status.")
               {:opts (second args)
                :res res})))))

(defn exec-with-success? [& args]
  (= 0 (:exit (apply exec args))))


;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
