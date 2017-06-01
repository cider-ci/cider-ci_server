(ns cider-ci.utils.tests.state
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.state :refer :all]
    ))


(deftest test-update-rows
  (testing "nested structure"
    (testing "adding a new entry"
      (is (= {:a {:b {:1 {:x 7}}}}
             (update-rows
               {}
               {:1 {:x 7}}
               [:a :b]))))
    (testing "updating an existing entry while leaving missing values to their original state"
      (is (= {:a {:b {:1 {:x 4 :y 7}}}}
             (update-rows
               {:a {:b {:1 {:x 3 :y 7}}}}
               {:1 {:x 4}}
               [:a :b]))))
    (testing "removing an entry while updating an existing one"
      (is (= {:a {:b {:1 {:x 4 :y 7}}}}
             (update-rows
               {:a {:b {:1 {:x 3 :y 7}
                        :2 {:z 9}}}}
               {:1 {:x 4}}
               [:a :b]))))))

