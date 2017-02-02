(ns cider-ci.builder.jobs.validator.script.template_environment_variables_tests
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
  (testing "template_environment_variables"
    (let [valid-job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  scripts:
                    script1:
                      template_environment_variables: false
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (validate! nil valid-job-spec)))
    (let [valid-job-spec
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  scripts:
                    script1:
                      template_environment_variables: 5
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (is (thrown? ValidationException (validate! nil valid-job-spec))))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
