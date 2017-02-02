(ns cider-ci.builder.jobs.validator.task.ports-tests
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
    [cider_ci.builder ValidationException]
    ))

(deftest test-validate!

  (testing "spec with valid ports"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  ports:
                    port-name:
                      min: 2000
                      max: 3000
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil job-spec))))

  (testing "invalid ports with bogus key"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  ports:
                    port-name:
                      bogus: blah
                      min: 2000
                      max: 3000
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Unknown Property.*"
            (validate! nil job-spec)))))

  (testing "invalid ports with property missing"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  ports:
                    port-name:
                      min: 2000
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Required Property Missing.*"
            (validate! nil job-spec)))))

  (testing "invalid max ports type"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  ports:
                    port-name:
                      min: 2000
                      max: '3000'
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Type Mismatch.*"
            (validate! nil job-spec)))))

  (testing "invalid ports range"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  ports:
                    port-name:
                      min: 3000
                      max: 2000
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Invalid Port Range.*"
            (validate! nil job-spec)))))
  )



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
