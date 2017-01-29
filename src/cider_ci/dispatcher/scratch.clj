; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.scratch
  (:require
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [honeysql.format :as sql-format]
    [honeysql.helpers :as sql-helpers]
    [honeysql.types :as sql-types]
    ))


(defn prototype-array-cast []
  (sql-format/format (sql-helpers/merge-where
                       [:= :repositories.git_url
                        (sql-types/call :ANY
                                        (assoc (honeysql.types/array ["http://github.com/x/y"])
                                               :cast "::varchar[]")
                                        )])))

(defn prototype-fn-cast []
  (sql-format/format (sql-helpers/merge-where
                       [:= :repositories.git_url
                        (sql-types/call :ANY
                                        (sql-types/call :cast (honeysql.types/array ["http://github.com/x/y"])
                                                        (keyword "varchar[]")
                                                        ))])))





