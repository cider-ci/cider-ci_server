(ns cider-ci.builder.spec-spec
  (:require
    [clj-yaml.core :as yaml]
    [cider-ci.builder.main :as main]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    )
  (:use 
    [cider-ci.builder.spec]
    [midje.sweet]))

(def x42-uuid 
  (java.util.UUID/fromString "cb993bdb-3a90-5842-8e12-4236ba30e276" )
  )

(facts "get-or-create-job-specification"
       (main/-main)
       (fact "the connection is up" (rdbms/get-ds) => truthy)
       (fact "get-or-create-job-specification yields row when not exists yet" 
             (jdbc/delete! (rdbms/get-ds) :job_specifications ["id = ?" x42-uuid])
             (:id (get-or-create-job-specification {:x 42})) => x42-uuid)
       (fact "get-or-create-job-specification yields row when exists"
             (get-or-create-job-specification {:x 42})
             (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM job_specifications WHERE id = ?",x42-uuid]))
               => truthy))

(facts "get-or-create-task-spec"
       (main/-main)
       (fact "the connection is up" (rdbms/get-ds) => truthy)
       (fact "get-or-create-task-spec yields row when not exists yet" 
             (jdbc/delete! (rdbms/get-ds) :task_specifications ["id = ?" x42-uuid])
             (:id (get-or-create-task-spec {:x 42})) => x42-uuid)
       (fact "get-or-create-task-spec yields row when exists"
             (get-or-create-task-spec {:x 42})
             (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM task_specifications WHERE id = ?",x42-uuid]))
               => truthy))

