; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.branch-updates.db-schema
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clj-time.core :as time]
    [schema.core :as schema]
    ))

(defn default []
  {
   :branches_count nil
   :update_info nil
   :branches_updated_at nil
   :last_commited_at nil
   :last_error nil
   :last_error_at nil
   :pending? false
   :state "initializing"
   :updated_at (time/now)
   })

(def schema
  {
   :branches_count (schema/maybe Number)
   :branches_updated_at (schema/maybe org.joda.time.DateTime)
   :last_commited_at (schema/maybe org.joda.time.DateTime)
   :last_error (schema/maybe String)
   :last_error_at (schema/maybe org.joda.time.DateTime)
   :update_info schema/Any
   :updated_at org.joda.time.DateTime
   :state (schema/enum
            "error"
            "initializing"
            "ok"
            "updating"
            "waiting"
            )
   :pending? Boolean
   })

(schema/validate schema (default))
