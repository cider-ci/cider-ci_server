; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.push-notifications.db-schema
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clj-time.core :as time]
    [schema.core :as schema]
    ))

(defn default []
  {
   :received_at nil
   :updated_at (time/now)
   })

(def schema
  {
   :received_at (schema/maybe org.joda.time.DateTime)
   :updated_at org.joda.time.DateTime
   })

(schema/validate schema (default))
