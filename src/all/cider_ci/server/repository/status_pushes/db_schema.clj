; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.status-pushes.db-schema
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
   :last_posted_at nil
   :state "unused"
   :updated_at (time/now)
   })

(def schema
  {
   :last_error (schema/maybe String)
   :last_error_at (schema/maybe org.joda.time.DateTime)
   :last_posted_at (schema/maybe org.joda.time.DateTime)
   :state (schema/enum
            "disabled"
            "error"
            "ok"
            "posting"
            "unused"
            "unaccessible"
            "waiting"
            )
   :updated_at org.joda.time.DateTime
   })

(schema/validate schema (default))
