(ns cider-ci.server.builder.jobs.validator.context-tests
  (:require
    [cider-ci.server.builder.jobs.validator.job :refer [validate!]]
    [cider-ci.server.builder.jobs.normalizer :refer [normalize-job-spec]]

    [clojure.test :refer :all]
    [yaml.core :as yaml]
    )
  (:import
    [cider_ci ValidationException]
    ))

(def job-spec-with-a-bogus-key-in-the-top-level-context
  (-> "
      key: job-with-bogus-key
      name: job-with-bogus-key
      context:
        bogus: whatever
      "
      yaml/parse-string
      normalize-job-spec
      ))

(def job-spec-with-a-bogus-key-in-a-subcontext
  (-> "
      key: job-with-bogus-key
      name: job-with-bogus-key
      context:
        contexts:
          sub1:
            bogus: whatever
      "
      yaml/parse-string
      normalize-job-spec
      ))


(deftest test-validate!
  (testing job-spec-with-a-bogus-key-in-a-subcontext
    (is (thrown-with-msg?
          ValidationException #"Validation Error - Unknown Property"
          (validate! nil job-spec-with-a-bogus-key-in-a-subcontext))))
  (testing job-spec-with-a-bogus-key-in-the-top-level-context
    (is (thrown-with-msg?
          ValidationException #"Validation Error - Unknown Property"
          (validate! nil job-spec-with-a-bogus-key-in-the-top-level-context)))))
