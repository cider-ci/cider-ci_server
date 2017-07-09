; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.migrations.schema-migrations
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.main]
    [cider-ci.server.dispatcher.main]
    [cider-ci.server.repository.main]
    [cider-ci.server.storage.main]
    [cider-ci.server.executors]
    [cider-ci.server.web :as web]
    [cider-ci.server.state]
    [cider-ci.server.push]
    [cider-ci.utils.app :as app]

    [clojure.java.jdbc :as jdbc]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]

    [logbug.catcher :as catcher]
    ))

;;; table ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def table-schema-migrations-exists-query
  (->> [" SELECT EXISTS "
        " (SELECT true "
        "   FROM  information_schema.tables "
        "   WHERE  table_schema = 'public' "
        "   AND  table_name = 'schema_migrations');  "]
       (map clojure.string/trim)
       (clojure.string/join " ")))

(def column-updated-at-exists-query
  (->> [" SELECT EXISTS "
        " (SELECT true "
        "   FROM  information_schema.columns "
        "   WHERE table_name = 'schema_migrations'"
        "   AND column_name = 'created_at')"]
       (map clojure.string/trim)
       (clojure.string/join " ")))

(defn setup-schema-migrations-table [ds]
  (jdbc/with-db-transaction [tx ds]
    (when-not (-> (jdbc/query tx table-schema-migrations-exists-query)
                  first :exists boolean)
      (jdbc/db-do-commands
        tx [(jdbc/create-table-ddl
              :schema_migrations
              [[:version :varchar "NOT NULL PRIMARY KEY"]])]))
    (when-not (-> (jdbc/query tx column-updated-at-exists-query)
                  first :exists boolean)
      (jdbc/db-do-commands
        tx ["ALTER TABLE schema_migrations ADD COLUMN
            created_at timestamp with time zone DEFAULT now() NOT NULL;"]))))

;;; versions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn versions [ds]
  (->> (jdbc/query ds ["SELECT * FROM schema_migrations"])
       (map :version)))

