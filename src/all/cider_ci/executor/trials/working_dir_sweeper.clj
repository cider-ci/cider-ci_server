; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.trials.working-dir-sweeper
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.trials.state]

    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.system :as system]

    [clj-time.core :as time]
    [clojure.java.io :refer [file]]
    [me.raynes.fs :as clj-fs]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    )
  (:import
    [java.io File]
    [java.nio.file Files Paths]
    [java.nio.file.attribute BasicFileAttributes PosixFileAttributes]
    [org.apache.commons.io FilenameUtils]
    [org.apache.commons.lang3 SystemUtils]
    ))

(defn- get-working-dir []
  (-> (get-config) :working_dir clj-fs/absolute clj-fs/normalized))

(defn- get-trial-dirs []
  (->> (clj-fs/list-dir (get-working-dir))
       (filter clj-fs/directory?)))

(defn- remove-working-dir [working-dir]
  (let [path (str working-dir)]
    (assert (.exists (file path)))
    (let [cmd (cond SystemUtils/IS_OS_UNIX ["rm" "-rf" path]
                    SystemUtils/IS_OS_WINDOWS ["RMDIR" "/s" "/q" path])]
      (system/exec! cmd {:timeout "1 Minute"}))))

(defn- delete-orphans []
  (doseq [working-dir (get-trial-dirs)]
    (when-let [base-name (clj-fs/base-name working-dir)]
      (when-not (cider-ci.executor.trials.state/get-trial base-name)
        (when-not (.exists (file (clojure.string/join
                                   [(str working-dir)
                                    File/separator "_cider-ci_keep"])))
          (logging/debug "deleting working-dir " working-dir)
          (catcher/snatch
            {} (remove-working-dir working-dir)))))))

(defdaemon "trial-working-dir-sweeper" 1 (delete-orphans))

(defn initialize []
 (start-trial-working-dir-sweeper))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
