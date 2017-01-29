; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.state
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.state.config :as config]
    [cider-ci.server.state.db :as db]
    [cider-ci.server.state.repositories :as repositories]
    [cider-ci.server.state.users :as users]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]

    ))

(defn get-db [] @db/db)

(defn watch-db [k fun]
  (add-watch db/db k
             (fn [_key _ref _old _new]
               (when (not= _old _new)
                 (fun _key _ref _old _new)))))

(defn initialize []
  (users/initialize)
  (config/initialize)
  (repositories/initialize))


;(debug/debug-ns *ns*)
