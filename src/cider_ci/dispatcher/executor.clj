; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.executor
  (:require
    [logbug.debug :as debug]
    [cider-ci.utils.config :as config]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [pandect.algo.sha1 :refer [sha1-hmac]]
    ))

(defn http-basic-password [executor]
  (sha1-hmac (str (:id executor)) (:secret (config/get-config))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

