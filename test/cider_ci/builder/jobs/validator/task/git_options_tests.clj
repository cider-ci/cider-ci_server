(ns cider-ci.builder.jobs.validator.task.git-options-tests
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

  (testing "spec with full set of valid git_options"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  git_options:
                    submodules:
                      include_match: ^.*$
                      exclude_match: $^
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil job-spec))))

  (testing "invalid git_options with bogus key"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  git_options:
                    bogus: blah
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Unknown Property.*"
            (validate! nil job-spec)))))

  (testing "invalid git_options/submodules with bogus key"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  git_options:
                    submodules:
                      bogus: blah
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Unknown Property.*"
            (validate! nil job-spec)))))


  (testing "invalid git_options without submodules key"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  git_options: {}
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Required Property Missing.*"
            (validate! nil job-spec)))))

  (testing "invalid git_options/submodules with both matchers missing"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  git_options:
                    submodules: {}
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
