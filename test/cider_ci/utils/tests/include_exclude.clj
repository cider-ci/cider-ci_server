(ns cider-ci.utils.tests.include-exclude
  (:refer-clojure :exclude [filter])
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.include-exclude :refer :all]
    ))

(deftest include-exclude-filter

  (testing "includes?"
    (is (= true (includes? ".*Bar.*" "FooBarBaz")))
    (is (= true (includes? true "FooBarBaz")))
    (is (= false (includes? "" "FooBarBaz"))))

  (testing "not-excludes?"
    (is (= true (not-excludes? "" "FooBarBaz")))
    (is (= true (not-excludes? false "FooBarBaz")))
    (is (= false (not-excludes? ".*Bar.*" "FooBarBaz"))))

  (testing "filter"

    (testing "pure boolean filters"
      (testing "a true exclude-match will exclude anything
               no matter the include-match"
        (is (= (filter true true ["blah" ""]) []))
        (is (= (filter false true ["blah" ""]) [])))

      (testing "a false include-match will exclude anything
               no matter the exclude-match"
        (is (= (filter false true ["blah" ""]) []))
        (is (= (filter false false ["blah" ""]) [])))

      (testing "a true include-match and a false exclude-match
               will preserve the collection"
        (is (= (filter true false ["blah" ""]) ["blah" ""]))))


    (testing "include string matchers"

      (testing "a substring match preserves containing strings"
        (is (= (filter "x" false ["abc" "wxy" "xyz"]) ["wxy" "xyz"])))

      (testing "a full matcher only preserves full matches"
        (is (= (filter "^wxy$" false ["abc" "wxy" "xyz"]) ["wxy"])))

      (testing "a postfix matcher only preserves postfix matches"
        (is (= (filter "yz$" false ["abc" "wxyz" "xyz"]) ["wxyz" "xyz"]))))

    (testing "exclude string matchers"
      (testing "a matching sub exclude-string excludes"
        (is (= (filter true "x" ["abc" "wxy" "xyz"]) ["abc"])))

      (testing "a empty string exclude-matcher doesn't exclude anything"
        (is (= (filter true "" ["abc" ""]) ["abc" ""])))

      )))
