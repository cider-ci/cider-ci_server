(ns cider-ci.server.builder.jobs.validator.script.script-dependencies-tests
  (:require
    [cider-ci.server.builder.jobs.validator.job :refer [validate!]]
    [cider-ci.server.builder.jobs.normalizer :refer [normalize-job-spec]]

    [clojure.test :refer :all]
    [yaml.core :as yaml]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))

(alter-var-root
  (var cider-ci.utils.config/get-config)
  (fn [f]
    (fn []
      {:constants
       {
        :STATES
        {
         :SCRIPT #{"defective" "failed" "waiting" "aborted" "executing" "pending" "skipped" "passed"}
         :JOB #{"aborting" "defective" "failed" "aborted" "executing" "pending" "passed"}
         }
        }})))

(deftest spec-with-valid-script-dependencies
  (let [job-spec
        (-> "
            key: job-key
            name: job-name
            tasks:
              task1:
                scripts:
                  s1:
                    body: test a = a
                  s2:
                    body: test a = a
                    start_when:
                      when-s1-passed:
                        script_key: s1
                        states: ['passed']
            "
            yaml/parse-string
            normalize-job-spec
            )]
    (is (validate! nil job-spec))))

(deftest spec-with_in-valid-script-dependency-without-key-ref
  (let [job-spec
        (-> "
            key: job-key
            name: job-name
            tasks:
              task1:
                scripts:
                  s1:
                    body: test a = a
                  s2:
                    body: test a = a
                    start_when:
                      when-s1-passed:
                        states: ['passed']
            "
            yaml/parse-string
            normalize-job-spec
            )]
    (is (thrown-with-msg?
          ValidationException #".*Required Property Missing.*"
          (validate! nil job-spec)))))

(deftest  spec-with-in-valid-script-dependency-with-illegal-state
  (let [job-spec
        (-> "
            key: job-key
            name: job-name
            tasks:
              task1:
                scripts:
                  s1:
                    body: test a = a
                  s2:
                    body: test a = a
                    start_when:
                      when-s1-passed:
                        script_key: s1
                        states: ['success']
            "
            yaml/parse-string
            normalize-job-spec
            )]
    (is (thrown-with-msg?
          ValidationException #".*Illegal State.*"
          (validate! nil job-spec)))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
