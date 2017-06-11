; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.exec.terminator
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:import
    [java.io File]
    [org.apache.commons.exec ExecuteWatchdog]
    [org.apache.commons.lang3 SystemUtils]
    )
  (:require
    [cider-ci.executor.scripts.exec.shared :refer :all]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.fs :as ci-fs]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.set :refer [difference union]]
    [clojure.string :as string :refer [split trim]]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

;### termination ##############################################################
; It seems not possible to guarantee that all subprocesses are killed.  The
; default strategy is to rely on "Apache Commons Exec"  which sometimes works
; but is unreliable in many cases, e.g. when the script starts in a new
; sub-shell.  On Linux and MacOS we recursively find all subprocesses via `ps`,
; see also `ps axf -o user,pid,ppid,pgrp,args`, and then kill those. This works
; well unless double forks are used which are nearly impossible to track with
; reasonable effort.

(defn create-watchdog []
  (ExecuteWatchdog. (ExecuteWatchdog/INFINITE_TIMEOUT) ))

(defn- get-child-pids-linux [pids]
  (try (-> (commons-exec/sh
             ["ps" "--no-headers" "-o" "pid" "--ppid" (clojure.string/join "," pids)])
           deref
           :out
           trim
           (split #"\n")
           (#(map trim %))
           set)
       (catch Exception _
         (set []))))

(defn- get-child-pids-mac-os [pids]
  (try (->> (-> (commons-exec/sh ["ps" "x" "-o" "pid ppid"])
                deref
                :out
                trim
                (split #"\n")
                (#(map trim %))
                rest)
            (map #(split % #"\s+"))
            (filter #(some (-> % second list set) pids))
            (map first)
            set)
       (catch Exception _
         (set []))))

(defn- get-child-pids [pids]
  ; TODO use IS_OS_....
  (case (System/getProperty "os.name")
    "Linux" (get-child-pids-linux pids)
    "Mac OS X" (get-child-pids-mac-os pids)
    (throw (IllegalStateException. (str "get-child-pids is not supported for "
                                         (System/getProperty "os.name"))))))

(defn- add-descendant-pids [pids]
  (logging/debug 'add-descendant-pids pids)
  (let [child-pids (get-child-pids pids)
        result-pids (union pids child-pids)]
    (logging/debug {:pids pids :result-pids result-pids})
    (if (= pids result-pids)
      result-pids
      (add-descendant-pids result-pids))))

(defn pid-file-path [params]
  (let [private-dir (-> params :private_dir)
        script-name (-> params :name)]
    (str private-dir (File/separator) (ci-fs/path-proof script-name) ".pid")))

(defn- get-initial-pids-or-nil [params]
  (->> (pid-file-path params)
       slurp
       (#(clojure.string/split % #"\s+|,|;"))
       (map  clojure.string/trim)
       (filter (complement clojure.string/blank?))
       seq doall))

(defn- kill-pid [pid]
  (let [cmd ["kill" "-KILL" pid]
        res (deref (commons-exec/sh cmd))]
    (logging/debug 'killed {:cmd cmd :res res})
    res))

(defn- kill-all-pids [pids]
  (->> pids
       (map #(future (kill-pid %)))
       (map deref)
       doall))

(defn- terminate-via-process-tree [script-atom]
  (if-let [initial-pids (get-initial-pids-or-nil @script-atom)]
    (let [pids (add-descendant-pids (set initial-pids))]
      (kill-all-pids pids))
    (logging/warn "no pids present for " script-atom)))

(defn- terminate-via-taskkill [script-atom]
  (if-let [pid (-> @script-atom get-initial-pids-or-nil first)]
    (let [cmd ["taskkill" "/F" "/T" "/PID" pid]
          res (deref (commons-exec/sh cmd))]
      (logging/debug 'killed {:cmd cmd :res res})
      res)))

(defn- terminate-via-commons-exec-watchdog [script-atom]
  (.destroyProcess (:watchdog @script-atom)))

(defn- wait-for-realized-or-expired-not-over [script-atom ds]
  (while (and (not (realized? (-> @script-atom :exec-future)))
              (not (expired? script-atom ds)))
    (Thread/sleep 1000)))

(defn terminate [script-atom]
  (cond
    SystemUtils/IS_OS_UNIX (terminate-via-process-tree script-atom)
    SystemUtils/IS_OS_WINDOWS (terminate-via-taskkill script-atom)
    :else (do (logging/warn "Only the parent process is killed on this system.")
              (terminate-via-commons-exec-watchdog script-atom)))
  (wait-for-realized-or-expired-not-over script-atom (time/seconds 15))
  (when-not (realized? (-> @script-atom :exec-future))
    (logging/warn "Script execution was not terminated after 15 Secs overtime." script-atom)
    (terminate-via-commons-exec-watchdog script-atom)
    (wait-for-realized-or-expired-not-over script-atom (time/seconds 30))
    (when-not (realized? (-> @script-atom :exec-future))
      (throw (IllegalStateException.
               (str "Script exec could not be terminated after 30 secs overtime." script-atom))))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
;(debug/debug-ns *ns*)
