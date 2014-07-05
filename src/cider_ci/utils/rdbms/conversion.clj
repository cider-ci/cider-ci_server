; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.rdbms.conversion
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time-core]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    )
  (:import org.postgresql.util.PGobject)
  )   

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defn convert-to-uuid [value]
  (case (.getName (type value))
    "java.lang.String" (java.util.UUID/fromString value)
    value))

(defn convert-to-varchar [value]
  (str value))

(defn convert-to-timestamp [value]
  (time-coerce/to-sql-time 
    (time-format/parse (time-format/formatters :date-time) value)))

(defn convert-to-json [value]
  (case (.getName (type value))
    "org.postgresql.util.PGobject" value
    "java.lang.String" (doto (PGobject.)
                         (.setType "json")
                         (.setValue value))
    (convert-to-json (json/write-str value))))

;(convert-to-json {:x 5})
   

(defonce _convert-to-type  (atom nil))
(defn convert-to-type [type-name value]
  (reset! _convert-to-type [type-name value])
  (logging/debug convert-to-type [type-name value])
  (let [res
        (case type-name
          "uuid" (convert-to-uuid value)
          "varchar" (convert-to-varchar value)
          "timestamp" (convert-to-timestamp value)
          "json" (convert-to-json value)
          (do
            (logging/warn "use default for " type-name)
            value))]
    (logging/debug "converted: " res)
    res
    ))
    

(defn get-column-type-name [table-metadata column-name]
  (-> table-metadata (:columns) (column-name) (:type_name)))


(defn convert-pair [table-metadata k v]
  (logging/debug convert-pair [k v])
  (let [type-name (get-column-type-name table-metadata k)]
    [k (convert-to-type type-name v)]
    ))

(defonce _convert-parameters (atom nil))
(defn convert-parameters [table-metadata params]
  (reset! _convert-parameters [table-metadata params])
  (into {} 
        (map 
          (fn [pair] 
            (logging/debug pair)
            (let [[k v] pair]
              (convert-pair table-metadata k v)
              )) params)))
