(ns cider-ci.utils.tests.core
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.core :refer :all]
    [clj-yaml.core :as yaml]
    ))

(deftest test-to-cistr
  (testing  "invariant (= s (-> s keyword to-cistr))"
    (doseq [s ["x" ":y" "a/b/c"]]
      (is (= s (-> s keyword to-cistr))))))

(deftest test-to-ciset
  (is (= (to-ciset
           {:x false
            :a/b/c true})
         #{"a/b/c"})))

(deftest test-to-ciset
  (is (= (to-ciset {:x true
                    "y" true
                    :z false})
         #{"x" "y"}))
  (is (thrown? Exception (to-ciset 5)))
  (is (thrown? Exception (to-ciset "a")))
  (is (thrown? Exception (to-ciset :some/kw))))

(deftest test-to-cisetmap
  (testing "maps are largely preserved but keys are mapped to keywrods"
    (is (= (to-cisetmap {:x true
                         "y" true
                         :z false})
           {:x true
            :y true
            :z false})))
  (testing "vectors are converted to cimaps"
    (is (= (to-cisetmap [:x "y" :z])
           {:x true
            :y true
            :z true}
           )))
  (testing "lists are converted to cimaps"
    (is (= (to-cisetmap '(:x "y" :z))
           {:x true
            :y true
            :z true}
           )))
  (testing "string argument raises an exeception"
    (is (thrown? Exception (to-cisetmap "x"))))
  (testing "integer argument raises an exeception"
    (is (thrown? Exception (to-cisetmap 7))))
  (testing "keyword argument raises an exeception"
    (is (thrown? Exception (to-cisetmap :x)))))

(deftest testing-to-cisetmap-invariant
  (doseq [v [[:a :a/b/c "x"]
             '(:a :a/b/c "x")
             #{:a :a/b/c "x"}
             ]]
    (is (= (-> v to-ciset)
           (-> v to-ciset to-cisetmap to-ciset)))))

(deftest test-deep-merge-
  (is (= (deep-merge
           (yaml/parse-string
             "x y z:
                x: 42")
           (yaml/parse-string
             "x y z:
                y: 49")
           (yaml/parse-string
             "x y z:
                z/z: 3.14")
           (yaml/parse-string
             "a b c: 7"))
         {(keyword "x y z") {:x 42 :y 49 :z/z 3.14}
          (keyword "a b c") 7 })))

