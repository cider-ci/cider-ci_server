; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.util
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [clj-http.client :as http-client]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn sort-map [m]
  (into {} (sort m)))

(defn sort-map-recursive [m]
  (->> m
       (clojure.walk/prewalk
         (fn [el]
           (if (map? el)
             (sort-map el)
             el)
           ))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
