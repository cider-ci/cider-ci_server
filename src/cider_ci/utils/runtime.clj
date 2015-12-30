; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.runtime
  (:require
    [logbug.debug :as debug]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    )
  (:import
    [humanize Humanize]
    ))

(defn check-memory-usage []
  (System/gc)
  (let [rt (Runtime/getRuntime)
        max-mem (.maxMemory rt)
        total-mem (.totalMemory rt)
        free-mem (.freeMemory rt)
        free-ratio (double (/ free-mem max-mem))
        ok? (> free-ratio 0.05)
        stats {"Max" (Humanize/binaryPrefix max-mem)
               "Total" (Humanize/binaryPrefix total-mem)
               "Free" (Humanize/binaryPrefix free-mem)
               :Free-Ratio (Double/parseDouble (String/format "%.2f" (into-array [free-ratio])))
               :OK? ok?
               :status (if ok?  "OK" "CRITICAL")}]
    (when-not ok?
      (logging/fatal stats))
    stats))

;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
