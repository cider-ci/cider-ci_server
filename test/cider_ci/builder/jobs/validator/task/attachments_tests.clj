(ns cider-ci.builder.jobs.validator.task.attachments-tests
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

  (testing "spec with valid trial_attachments"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  trial_attachments:
                    logs:
                      content_type: text/plain
                      include_match: '^.*log$'
                      exclude_match: '^.*log$'
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil job-spec))))

  (testing "invalid tree_attachment with bogus key"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  tree_attachments:
                    logs:
                      bogus: 42
                      content_type: text/plain
                      include_match: '^.*log$'
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown-with-msg?
            ValidationException #".*Unknown Property.*"
            (validate! nil job-spec)))))

  (testing "invalid trial_attachments with include_match property missing"
    (let [job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  trial_attachments:
                    logs:
                      content_type: text/plain
                      exclude_match: '^.*log$'
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
