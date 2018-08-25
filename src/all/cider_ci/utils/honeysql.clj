; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.honeysql
  (:refer-clojure :exclude [format update])
  (:require
    [honeysql.format :as format]
    [honeysql.helpers :as helpers :refer [build-clause]]
    [honeysql.types :as types]
    [honeysql.util :as util :refer [defalias]]

    [honeysql-postgres.helpers :as pg-helpers]
    [honeysql-postgres.format :as pg-format]

    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))

; regex
(defmethod format/fn-handler "~*" [_ field value]
  (str (format/to-sql field) " ~* " (format/to-sql value)))

; ilike
(defmethod format/fn-handler "~~*" [_ field value]
  (str (format/to-sql field) " ~~* " (format/to-sql value)))

(defn dedup-join [honeymap]
  (assoc honeymap :join
         (reduce #(let [[k v] %2] (conj %1 k v)) []
                 (clojure.core/distinct (partition 2 (:join honeymap))))))

(defn format
  "Calls honeysql.format/format with removed join duplications in sql-map."
  [sql-map & params-or-opts]
  (apply format/format [(dedup-join sql-map) params-or-opts]))


(defalias call types/call)
(defalias param types/param)
(defalias raw types/raw)

(defalias format-predicate format/format-predicate)
(defalias quote-identifier format/quote-identifier)

(defalias delete-from helpers/delete-from)
(defalias from helpers/from)
(defalias group helpers/group)
(defalias insert-into helpers/insert-into)
(defalias join helpers/join)
(defalias limit helpers/limit)
(defalias merge-join helpers/merge-join)
(defalias merge-where helpers/merge-where)
(defalias modifiers helpers/modifiers)
(defalias offset helpers/offset)
(defalias order-by helpers/order-by)
(defalias select helpers/select)
(defalias sset helpers/sset)
(defalias update helpers/update)
(defalias values helpers/values)
(defalias where helpers/where)
(defalias returning helpers/returning)
;(defalias using helpers/using)

(defalias do-nothing pg-helpers/do-nothing)
(defalias do-update-set pg-helpers/do-update-set)
(defalias do-update-set! pg-helpers/do-update-set!)
(defalias on-conflict pg-helpers/on-conflict)
(defalias returning pg-helpers/returning)
(defalias upsert pg-helpers/upsert)


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

