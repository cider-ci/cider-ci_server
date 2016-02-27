; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.sync.ping
  (:require
    [cider-ci.dispatcher.executor :as executor-entity]
    [logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

(defn update-last-ping-at [executor]
  (->
    (jdbc/execute! (rdbms/get-ds)
                   ["UPDATE executors SET last_ping_at = now()
                    WHERE executors.id = ?" (:id executor)])
    first
    (> 0)))


