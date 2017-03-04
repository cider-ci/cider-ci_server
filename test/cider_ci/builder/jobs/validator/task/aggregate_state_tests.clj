(ns cider-ci.builder.jobs.validator.task.aggregate_state_tests
  (:require
    [cider-ci.builder.jobs.validator.job :refer [validate!]]
    [cider-ci.builder.jobs.normalizer :refer [normalize-job-spec]]

    [clojure.test :refer :all]
    [clj-yaml.core :as yaml]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))


(deftest test-validate!

  (testing "spec with valid satisfy-any for aggregate_state"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  aggregate_state: satisfy-any"
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil job-spec))))

  (testing "spec with valid satisfy-last for aggregate_state"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  aggregate_state: satisfy-last"
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil job-spec))))

  (testing "spec with invalid value for aggregate_state"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  aggregate_state: bogus"
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
                    ValidationException #".*Illegal Value.*"
                    (validate! nil job-spec)))))
  )



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
