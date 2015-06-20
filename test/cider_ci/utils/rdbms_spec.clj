(ns cider-ci.utils.rdbms_spec
  (:require
    [clojure.tools.logging :as logging]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    )
  (:use
    [midje.sweet])
  )

(def db-spec {:adapter "postgresql"
              :classname "org.postgresl.Driver"
              :subname "//localhost:5432/cider-ci_development"
              :user "cider-ci"
              :password "cider-ci"
              :subprotocol "postgresql"
              :max_pool_size 2 })

(facts "using rdbms to create a pooled connection"

       (rdbms/reset)

       (fact (rdbms/get-ds) => nil)
       (fact (rdbms/get-db-spec) => nil)
       (fact (rdbms/get-tables-metadata) => nil)

       (rdbms/initialize {:subprotocol "sqlite"
                          :subname ":memory:"
                          :max_pool_size 7 })

       (let [ds  (rdbms/get-ds)]
         (logging/info ds)
         (fact "pool size" (.getMaxPoolSize (:datasource (rdbms/get-ds))) => 7)
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS t ;") => truthy)
         (fact "create table" (jdbc/db-do-commands ds "CREATE TABLE t (i integer);") => truthy)
         (fact "insert" (jdbc/insert! ds "t" {:i 42}) => truthy )
         (fact "query" (jdbc/query ds ["SELECT * FROM t"]) => [{:i 42}])
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS t ;") => truthy)
         )

       )


(facts "Using the postgresql json type"

       (rdbms/reset)

       (rdbms/initialize db-spec )

       (let [ds  (rdbms/get-ds)]
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS test ;") => truthy)
         (fact "create table" (jdbc/db-do-commands ds "CREATE TABLE test (data json);") => truthy)
         (fact "insert" (jdbc/insert! ds "test" {:data {:array ["s1" 7], :number 42}}) => truthy )
         (fact "query" (first (jdbc/query ds ["SELECT * FROM test"])) =>  {:data {:array ["s1" 7], :number 42}})
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS test ;") => truthy)
         )

       (rdbms/reset)

       )


(facts "Using the postgresql array type"

       (rdbms/reset)

       (rdbms/initialize db-spec)

       (let [ds  (rdbms/get-ds)]
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS test ;") => truthy)
         (fact "create table" (jdbc/db-do-commands ds "CREATE TABLE test (sarray text[]);") => truthy)
         (fact "insert" (jdbc/insert! ds "test" {:sarray ["s1","s2"]}) => truthy )
         (fact "query" (into [] (:sarray (first (jdbc/query ds ["SELECT * FROM test"])))) =>  ["s1","s2"])
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS test ;") => truthy)
         )

       (rdbms/reset)

       )






