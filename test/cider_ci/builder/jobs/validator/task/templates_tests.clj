(ns cider-ci.builder.jobs.validator.task.templates-tests
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

  (testing "spec with valid templates"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  templates:
                    some-template:
                      src: path-to-template-file
                      dest: path-to-dest-file
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil job-spec))))

  (testing "invalid template with bogus key"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  templates:
                    some-template:
                      bogus: 42
                      src: path-to-template-file
                      dest: path-to-dest-file
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Unknown Property.*"
            (validate! nil job-spec)))))

  (testing "invalid templates with dest property missing"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  templates:
                    some-template:
                      src: path-to-template-file
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Required Property Missing.*"
            (validate! nil job-spec)))))

    )



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
