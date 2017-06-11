(ns cider-ci.utils.markdown-tests
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.markdown :refer :all]
    ))

(deftest test-md2html
  (is (= "<h1>Test</h1>" (md2html "# Test"))))
