(ns cider-ci.builder.tasks-spec
  (:require
    [clj-yaml.core :as yaml]
    )
  (:use 
    [cider-ci.builder.tasks]
    [midje.sweet]))


(facts "deep-merge" 
       (fact (deep-merge {:x 1} {:y 2}) => {:x 1 :y 2})
       (fact (deep-merge {:x 1} {:x 42 :y 2}) => {:x 42 :y 2})
       (fact (deep-merge {:x {:z 42}} {:x nil}) =>  {:x nil}))


(def test-task-definition 
  {:name "Task" 
   :v 1 
   :ports {:web {:min 8800 :max 89999}}
   :scripts {:prepare {:body "touch go" :position 1 }
             :main {:position 2 :environment_variables {:x 1 :y 2}}}})

(def test-script-defaults
  {:environment_variables {:y 42 :z 3}})


(def test-task-defaults
  {:ports {:xvnc {:min 5900 :max 5999}}})

(facts "build-scripts" 
       (let [built-scripts (build-scripts test-task-definition test-script-defaults)]
         (fact (set (keys built-scripts)) => #{:main :prepare})
         (fact (-> built-scripts :prepare :environment_variables) =>  {:y 42 :z 3})
         (fact (-> built-scripts :main :environment_variables) =>  {:x 1 :y 2 :z 3})))


(facts "build-task" 
       (let [built-task (build-task 
                          test-task-definition 
                          test-task-defaults
                          test-script-defaults
                          "Prefix")]
         (fact (:name built-task) => "Prefix » Task")
         (fact (:ports built-task) =>  {:web {:min 8800 :max 89999} :xvnc {:min 5900 :max 5999}})
         (fact (-> built-task :scripts :main :environment_variables) =>  {:x 1 :y 2 :z 3})
         ))

(facts "build-tasks-for-contexts-sequence"
       (let [spec (yaml/parse-string 
                    (slurp "test/data/tasks_spec.yml"))
             tasks (build-tasks-for-contexts-sequence spec {} {} "Prefix")
             task_in_l1-2-3  (some #(when (= (:name %) "Prefix » L1 » L1.2 » L1.2.3 » Task in L1.2.3")%) tasks)
             ]
         (fact tasks => truthy)
         (fact (count tasks) => 6)
         (fact (->> tasks (map :name) sort) =>  (sort ["Prefix » L1 » L1.1 » Task in L1.1" 
                                                       "Prefix » L1 » L1.2 » L1.2.3 » Task in L1.2.3" 
                                                       "Prefix » L1 » L1.2 » Task 1 in L1.2" 
                                                       "Prefix » L1 » L1.2 » Task 2 in L1.2" 
                                                       "Prefix » L1 » Task in L1" 
                                                       "Prefix » L2 » Task in L2"]))
         (fact task_in_l1-2-3 => truthy)
         (fact (:scripts task_in_l1-2-3) => {:main {:environment_variables {:e 7}}})
         ))

