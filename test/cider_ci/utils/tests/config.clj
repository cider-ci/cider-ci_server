(ns cider-ci.utils.tests.config
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.config :refer :all]
    [clj-logging-config.log4j :as logging-config]
    ))


(deftest custom_resource
  (initialize {:resource-names ["config_custom.yml"]})
  (is (= (-> (get-config)
             :the_custom_resource_defined_key)
         7))
  (is (= (-> (get-config)
             :the_default_resource_defined_key)
         nil)
      "the in the default resource defined key is no longer visible"))

(deftest default_resource
  (initialize {})
  (is (= (-> (get-config)
             :the_default_resource_defined_key)
         42))
  (is (= (-> (get-config)
             :the_custom_resource_defined_key)
         nil)))

(deftest default_config_file
  (initialize {})
  (is (= (-> (get-config)
             :default_config_file_defined_key)
         27))
  (is (= (-> (get-config)
             :custom_config_file_defined_key)
         nil)))

(deftest custom_config_file
  (initialize {:filenames ["config/custom_config.yml"]})
  (is (= (-> (get-config)
             :default_config_file_defined_key)
         nil))
  (is (= (-> (get-config)
             :custom_config_file_defined_key)
         69)))

(deftest defaults
  (testing "defaults are considered"
    (initialize {:defaults
                 {:some_default "The value of :some_default."}})
    (is (= (-> (get-config)
               :some_default)
           "The value of :some_default.")))
  (testing "all other values override defaults"
    (initialize {:defaults
                 {:will_be_overridden "will_be_overridden"
                  :the_default_resource_defined_key "something-else"
                  :default_config_file_defined_key "something-else"}
                 :overrides
                 {:will_be_overridden "is_overridden"}})
    (is (= (-> (get-config)
               :the_default_resource_defined_key)
           42))
    (is (= (-> (get-config)
               :default_config_file_defined_key)
           27))
    (is (= (-> (get-config)
               :will_be_overridden)
           "is_overridden"))))

(deftest bogus-options-will-exit
  (with-redefs [exit! (constantly "EXITED!")]
    (is (= (initialize {:bogus nil})
           "EXITED!"))))


(deftest overrides
  (initialize {:overrides {:default_config_file_defined_key 99}})
  (is (= (-> (get-config)
             :default_config_file_defined_key)
         99)))



