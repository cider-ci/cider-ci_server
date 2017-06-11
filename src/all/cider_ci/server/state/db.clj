; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.state.db
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require

    [cider-ci.utils.self :as self]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]

    ))

(def db (atom {:release (self/release)
               :repositories {}
               :users {}}))

(defn watch [k fun]
  (apply add-watch [db k fun]))

(add-watch db :debug-watch
           (fn [_ _ before after]
             (logging/debug 'DB-CHANGE {:before (:repositories before) :after (:repositories after)})))

;(fipp.edn/pprint @db)
;(debug/debug-ns *ns*)
