; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.migrations
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.constants :refer [RUN-DEFAULTS]]
    [cider-ci.server.builder.main]
    [cider-ci.server.dispatcher.main]
    [cider-ci.server.executors-old]
    [cider-ci.server.migrations.433]
    [cider-ci.server.migrations.434]
    [cider-ci.server.migrations.435]
    [cider-ci.server.migrations.436]
    [cider-ci.server.migrations.438]
    [cider-ci.server.migrations.schema-migrations :as schema-migrations]
    [cider-ci.server.repository.main]
    [cider-ci.utils.url.jdbc :as jdbc-url]

    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.rdbms :as ds :refer [extend-pg-params]]
    [cider-ci.utils.url.jdbc]

    [clojure.java.jdbc :as jdbc]
    [clojure.pprint :refer [pprint]]
    [clojure.set :refer [difference intersection]]
    [clojure.tools.cli :refer [parse-opts]]
    [yaml.core :as yaml]


    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown]
    ))

(def migrations
  {"000" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/000_setup.sql"))))}
   "001" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/001_events.sql"))))}
   "002" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/002_settings.sql"))))}
   "003" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/003_users.sql"))))}
   "004" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/004_projects.sql"))))}
   "005" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/005_jobs.sql"))))}
   "006" {:up (fn [tx]
                (jdbc/execute!
                  tx (slurp (clojure.java.io/resource "migrations/006_executors.sql"))))}}) 

   ; "433" {:up cider-ci.server.migrations.433/up}
   ;"434" {:up cider-ci.server.migrations.434/up}
   ;"435" {:up cider-ci.server.migrations.435/up
   ;       :down cider-ci.server.migrations.435/down}
   ;"438" {:up cider-ci.server.migrations.438/up
   ;       :down cider-ci.server.migrations.438/down}})


;;; migrate ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn up [ds versions]
  (doseq [version (sort versions)]
    (jdbc/with-db-transaction [tx ds]
      (let [up-migration (:up (get migrations version))]
        (cond
          (fn? up-migration) (up-migration tx)
          (string? up-migration) (jdbc/execute! tx up-migration))
        (jdbc/insert! tx :schema_migrations {:version version})
        (println (str "Successfully applied up-migration " version))))))

(defn down [ds versions]
  (doseq [version (-> versions sort reverse)]
    (jdbc/with-db-transaction [tx ds]
      (if-let [down-migration (:down (get migrations version))]
        (do (cond
              (fn? down-migration) (down-migration tx)
              (string? down-migration) (jdbc/execute! tx down-migration))
            (jdbc/delete! tx :schema_migrations ["version = ?" version])
            (println (str "Successfully applied down-migration " version )))
        (throw (ex-info "Irreversible migration" {:version version}))))))

(defn compute [ds version]
  (schema-migrations/setup-schema-migrations-table ds)
  (let [migrated-versions (apply sorted-set (schema-migrations/versions ds))
        migration-versions (apply sorted-set (keys migrations))
        expected-present-migrations (if-not version
                                      migration-versions
                                      (apply sorted-set
                                             (subseq migration-versions
                                                     <= version)))
        expected-absent-migrations (if-not version
                                     []
                                     (apply sorted-set
                                            (subseq migration-versions
                                                    > version)))
        up-migrations (difference expected-present-migrations migrated-versions)
        down-migrations (intersection
                          expected-absent-migrations migrated-versions)]
    {:migrated-versions migrated-versions
     :migration-versions migration-versions
     :expected-absent-migrations expected-absent-migrations
     :expected-present-migrations expected-present-migrations
     :up-migrations up-migrations
     :down-migrations down-migrations}))

;(apply sorted-set ["x" "y"])


(defn migrate [version options]
  (catcher/with-logging {}
    (let [ds (ds/create-ds (assoc (:database-url options)
                                  :min-pool-size 1
                                  :max-pool-size 1))]
      (try (let [computed (compute ds version)
                 down-migrations (:down-migrations computed)
                 up-migrations (:up-migrations computed) ]
             (down ds down-migrations)
             (up ds up-migrations))
           (finally (ds/close-ds ds))))))

;(migrate "jdbc:postgresql://thomas:thomas@localhost/cider-ci" "0")


;;; recreate ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn recreate [options]
  (let [conn-ds (ds/create-ds (assoc (:database-url options)
                                     :database "template1"
                                     :min-pool-size 1
                                     :max-pool-size 1))
        database-name (-> options :database-url :database)
        disconnect-query (-> (sql/select :%pg_terminate_backend.pid
                                         :pid
                                         :usename
                                         :datname
                                         :state)
                             (sql/from :pg_stat_activity)
                             (sql/merge-where [:= :pg_stat_activity.datname database-name])
                             sql/format)]
    (try
      (logging/debug {:conn-ds conn-ds})
      (logging/debug {:disconnect-query disconnect-query})
      (logging/info {:disconnect (jdbc/query conn-ds disconnect-query)})
      (logging/info (:create_database
                      (jdbc/db-do-commands
                        conn-ds false [(str "DROP DATABASE IF EXISTS \"" database-name "\"")
                                       (str "CREATE DATABASE \"" database-name "\"")])))
      (finally (ds/close-ds conn-ds)))))

;(-main "-r" "true")

;;; cli, main ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn env-or-default [kw]
  (or (-> (System/getenv) (get (str kw) nil) presence)
      (get RUN-DEFAULTS kw nil)))

(def cli-options
  [["-h" "--help"]
   ["-d" "--database-url LEIHS_DATABASE_URL"
    (str "default: " (:CIDER_CI_DATABASE_URL RUN-DEFAULTS))
    :default (-> (env-or-default :CIDER_CI_DATABASE_URL)
                 jdbc-url/dissect extend-pg-params)
    :parse-fn #(-> % jdbc-url/dissect extend-pg-params)]
   ["-r" "--recreate" "Drops and then recreates the database from scratch."
    :default false
    :parse-fn #(yaml/parse-string (str %))
    ]
   ["-v" "--version VERSION" "Migrate to the specified version"
    :default (-> migrations keys sort reverse first)
    :parse-fn identity
    ]])

(defn usage [options-summary & more]
  (->> ["Cider-CI Server Migrations "
        ""
        "usage: cider-ci server migrate [<opts>] [<args>]"
        ""
        ""
        "Options:"
        options-summary
        ""
        ""

        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(defn show [computed]
  (->> ["Cider-CI Server Database-Migrations "
        ""
        "Status and to be applied down- and up-migrations:"
        ""
        (with-out-str (pprint computed))
        ""
        ""
        ]
       flatten (clojure.string/join \newline)))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     ; :return-fn (fn [_] (System/exit -1))
     }
    (let [{:keys [options arguments errors summary]} (parse-opts args cli-options :in-order true)
          {version :version} options]
      (cond
        (:help options) (println (usage summary {:args args :options options}))
        :else (do
                (when (:recreate options) (recreate options))
                (migrate version options))))))

; help
;(-main "-h")
;
; show
;(-main "-s" "-d" "jdbc:postgresql://thomas:thomas@localhost/cider-ci_v5")
;
; migrate
;(main "-d" "jdbc:postgresql://thomas:thomas@localhost/cider-ci_v4")
;
; rollback
;(main "-d" "jdbc:postgresql://thomas:thomas@localhost/cider-ci_v5" "-v" "0")


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/debug-ns 'clojure.tools.cli)
;(debug/debug-ns 'cider-ci.utils.config)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
