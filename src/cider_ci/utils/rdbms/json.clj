; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.rdbms.json
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    )
  (:import org.postgresql.util.PGobject))

(defn value-to-json-pgobject [value]
  (doto (PGobject.)
    (.setType "json")
      (.setValue (json/write-str value))))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value))

  clojure.lang.IPersistentVector
  (sql-value [value] (value-to-json-pgobject value)))

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      ;(logging/debug [type value])
      (case type
        "json" (json/read-str value)
        :else value))))

(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.jdbc4.Jdbc4Array 
  (result-set-read-column [pgobj metadata idx]
    (logging/debug [pgobj metadata idx])
    (.getArray pgobj)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


