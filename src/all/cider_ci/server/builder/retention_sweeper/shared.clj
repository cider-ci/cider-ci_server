; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.retention-sweeper.shared
  (:require
    [cider-ci.utils.config :refer [get-config parse-config-duration-to-seconds]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn retention-interval [var-sym]
  (catcher/snatch
    {:return-fn (fn [e]
                  (logging/warn (clojure.string/join ["Failed to parse duration for " var-sym
                                                      " using 100 Years!"]))
                  "100 years" )}
    (let [seconds (parse-config-duration-to-seconds var-sym)]
      (cond
        (> seconds (* 100 24 60 60)) (str (-> seconds (/ (* 24 60 60)) Math/ceil int) " days")
        (> seconds (* 100 60 60)) (str (-> seconds (/ 3600) Math/ceil int) " hours")
        :else (str (int seconds) " seconds")))))


