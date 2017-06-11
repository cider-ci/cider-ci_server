; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.push-hooks.db-schema
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clj-time.core :as time]
    [schema.core :as schema]
    ))

(defn default []
  {
   :last_error nil
   :last_error_at nil
   :hook nil
   :state "initializing"
   :updated_at (time/now)
   })

(def schema
  {
   :last_error (schema/maybe String)
   :last_error_at (schema/maybe org.joda.time.DateTime)
   :updated_at org.joda.time.DateTime
   :state (schema/enum
            "checking"
            "disabled"
            "error"
            "initializing"
            "ok"
            "unaccessible"
            "unavailable"
            "unmanaged"
            )
   :hook schema/Any
   })

(schema/validate schema (default))
