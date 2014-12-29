; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.rdbms.conversion
  (:require
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time-core]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    )
  (:import org.postgresql.util.PGobject)
  )   


(defonce ^:dynamic *tables-metadata* nil)

(defn convert-to-uuid [value]
  (logging/warn (str convert-to-uuid " is deprecated"))
  (case (.getName (type value))
    "java.lang.String" (java.util.UUID/fromString value)
    value))

(defn convert-to-varchar [value]
  (logging/warn (str convert-to-varchar " is deprecated"))
  (str value))

(defn convert-to-timestamp [value]
  (logging/warn (str convert-to-timestamp " is deprecated"))
  (time-coerce/to-sql-time 
    (time-format/parse (time-format/formatters :date-time) value)))

(defn convert-to-json [value]
  (logging/warn (str convert-to-json " is deprecated"))
  (case (.getName (type value))
    "org.postgresql.util.PGobject" value
    "java.lang.String" (doto (PGobject.)
                         (.setType "json")
                         (.setValue value))
    (convert-to-json (json/write-str value))))

(defn convert-to-jsonb [value]
  (logging/warn (str convert-to-jsonb " is deprecated"))
  (case (.getName (type value))
    "org.postgresql.util.PGobject" value
    "java.lang.String" (doto (PGobject.)
                         (.setType "jsonb")
                         (.setValue value))
    (convert-to-jsonb (json/write-str value))))


;(convert-to-json {:x 5})

(defn convert-to-type [type-name value]
  (logging/warn (str convert-to-type " is deprecated"))
  (let [res
        (case type-name
          "uuid" (convert-to-uuid value)
          "varchar" (convert-to-varchar value)
          "timestamp" (convert-to-timestamp value)
          "json" (convert-to-json value)
          "jsonb" (convert-to-jsonb value)
          (do
            (logging/warn "use default for " type-name)
            value))]
    (logging/debug "converted: " res)
    res
    ))
    

(defn get-column-type-name [table-metadata column-name]
  (logging/warn get-column-type-name " is deprecated")
  (-> table-metadata (:columns) (column-name) (:type_name)))


(defn convert-pair [table-metadata k v]
  (logging/warn convert-pair " is deprecated")
  (logging/debug convert-pair [k v])
  (let [type-name (get-column-type-name table-metadata k)]
    [k (convert-to-type type-name v)]
    ))

(defn convert-parameters [table-name params]
  (logging/warn (str convert-parameters " is deprecated"))
  (let [table-metadata (*tables-metadata* table-name)]
    (into {} 
          (map 
            (fn [pair] 
              (logging/debug pair)
              (let [[k v] pair]
                (convert-pair table-metadata k v)
                )) params))))



(defn filter-parameters [table-name params]
  (logging/warn (str filter-parameters " is deprecated"))
  (let [table-metadata (*tables-metadata* table-name)
        ks (set (keys (:columns table-metadata))) ]
    (select-keys params ks)
    ))


(defn initialize [tables-metadata]
  ;(logging/warn (str initialize " is deprecated"))
  (def ^:dynamic *tables-metadata* tables-metadata))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


