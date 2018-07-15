; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:import
    [java.io File])
  (:require
    [cider-ci.server.repository.state :as state]
    [cider-ci.utils.config :as config :refer [get-config]]
    [clojure.string :as string :refer [blank? split trim]])
  (:require
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]))

(defn repositories-fs-base-path []
  (let [path (:repositories-path  (get-config))]
    (assert (not (blank? path)))
    path))

(defn repository-fs-path [repository-or-id]
  (let [id (if (map? repository-or-id)
             (str (:id repository-or-id))
             repository-or-id)]
    (assert (not (blank? id)))
    (str (repositories-fs-base-path) (File/separator) id)))

