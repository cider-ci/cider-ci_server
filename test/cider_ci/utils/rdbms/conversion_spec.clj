(ns cider-ci.utils.rdbms.conversion_spec
  (:require 
    [clojure.tools.logging :as logging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.rdbms.conversion :as rdbms.conversion]
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

(facts ""


       (rdbms/reset)

       (rdbms/initialize db-spec)

       (let [ds  (rdbms/get-ds)]
         (fact "drop table" (jdbc/db-do-commands ds "DROP TABLE IF EXISTS test ;") => truthy)
         (fact "create table" (jdbc/db-do-commands ds "CREATE TABLE test 
                                                      (t text
                                                      ,i integer
                                                      ,json_data json
                                                      ,text_array text[]
                                                      ,ts timestamp without time zone
                                                      ,id uuid
                                                      );") => truthy))


       (rdbms/reset)

       (rdbms/initialize db-spec )

       (let [tables-metadata  (rdbms/get-tables-metadata)]
         (fact tables-metadata  => truthy )
         (fact (-> tables-metadata :test :columns :id :type_name)  => "uuid" ) 
         (fact (-> tables-metadata :test :columns :json_data :type_name)  => "json" ) 
         (fact (-> tables-metadata :test :columns :text_array :type_name)  => "_text" ) 
         (fact (-> tables-metadata :test :columns :t :type_name)  => "text" ) 
         (fact (-> tables-metadata :test :columns :i :type_name)  => "int4" ) 
         (fact (-> tables-metadata :test :columns :ts :type_name)  => "timestamp" ) 
         )


       (facts "converting to json" 
              (let [original {:json_data {:x 42}} 
                    converted (rdbms.conversion/convert-parameters :test original)
                    value (:json_data converted)
                    ]
                (fact (type value) => org.postgresql.util.PGobject)
                (fact (.getType value) => "json")
                (fact (.getValue value) => "{\"x\":42}")
                ))

       (facts "converting to timestamp" 
              (let [original {:ts "1970-07-21T06:09:40.610Z"} 
                    converted (rdbms.conversion/convert-parameters :test original)
                    value (:ts converted)
                    ]
                (fact (type value) => java.sql.Timestamp)
                ))

       (facts "filter-parameters" 
              (let [original {:t "Hello World!"
                              :nonsense "Blah"}
                    filtered (rdbms.conversion/filter-parameters :test original)
                    ]
                (fact (keys filtered) => [:t])
              ))



       (rdbms/reset)

       )

