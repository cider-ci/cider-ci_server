(ns cider-ci.builder.task-spec
  (:require
    [cider-ci.builder.main :as main]
    [cider-ci.builder.task :as task]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    )
  (:use 
    [midje.sweet]))


(facts "task/spec-map-to-array"
       (fact (task/spec-map-to-array {:linux true :windows false}) => ["linux"])
       )

(facts task/create-db-task
       (main/-main)
       (jdbc/delete! (rdbms/get-ds) :executions ["name = 'test execution'"])
       (let [execution (first (jdbc/insert! 
                                (rdbms/get-ds) 
                                :executions {:tree_id "a-fake-tree-id" :name "test execution" }))]
         (fact (task/create-db-task {:traits {:linux true :windows false} 
                                     :execution_id (:id execution)
                                     :name "test task"
                                     :scripts []})
               => truthy)
         (let [task  (first (jdbc/query (rdbms/get-ds) ["SELECT * FROM tasks WHERE name = 'test task'"]))]
           (fact task => truthy)
           (fact (into [] (:traits task)) => ["linux"])
           )))
