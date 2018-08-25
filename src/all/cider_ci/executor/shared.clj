; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [str keyword]]
    [cider-ci.utils.config :as config :refer [get-config]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

;##############################################################################

(defn- get-current-user-name []
  (System/getProperty "user.name"))

(defn exec-user-name []
  (or (-> (get-config) :exec_user :name)
      (get-current-user-name)))

;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
