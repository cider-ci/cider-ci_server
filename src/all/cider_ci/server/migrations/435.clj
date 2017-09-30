; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.migrations.435
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [clojure.java.jdbc :as jdbc]
    ))

(defn up [tx]
  (jdbc/execute!
    tx (slurp (clojure.java.io/resource
                "migrations/435_up_create_events.sql"))))

(defn down [tx]
  (jdbc/execute!
    tx (slurp (clojure.java.io/resource
                "migrations/435_down_create_events.sql"))))

