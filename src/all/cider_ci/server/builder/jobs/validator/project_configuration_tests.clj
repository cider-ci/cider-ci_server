(ns cider-ci.server.builder.jobs.validator.project-configuration-tests
  (:require
    [cider-ci.server.builder.jobs.validator.project-configuration :as project-configuration-validator]
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
         }}}
      )))

(deftest valid-project-configuration
  (let [project-spec
        (-> "
            jobs:
              job1:
                context: {}
                depends_on:
                  'job0 in the submodule library has passed':
                    type: job
                    job_key: job0
                    submodule: ['library']
                    states: ['passed']
                run_when:
                  'branch master has been updated':
                    type: branch
                    include_match: ^master$
            "
            yaml/parse-string
            )]
    (is (project-configuration-validator/validate! project-spec))))

(deftest invalid-project-configuration-with-bogus-key
  (let [project-spec
        (-> "
            bogus: whatever
            jobs: {}
            "
            yaml/parse-string
            )]
    (is (thrown-with-msg?
          ValidationException #".*Unknown Property.*"
          (project-configuration-validator/validate! project-spec)))))

(deftest invalide-submodule-in-job-dependency
  (let [project-spec
        (-> "
            jobs:
              job1:
                depends_on:
                  'job0 in the submodule library has passed':
                    type: job
                    job_key: job0
                    submodule: [42]
                    states: ['passed']
            "
            yaml/parse-string
            )]
    (is (thrown-with-msg?
          ValidationException #".*Type Mismatch.*"
          (project-configuration-validator/validate! project-spec)))))

(deftest invalide-state-in-job-dependency
  (let [project-spec
        (-> "
            jobs:
              job1:
                depends_on:
                  'job0 in the submodule library has passed':
                    type: job
                    job_key: job0
                    states: ['success']
            "
            yaml/parse-string
            )]
    (is (thrown-with-msg?
          ValidationException #".*Illegal State.*"
          (project-configuration-validator/validate! project-spec)))))

(deftest invalide-branch-with-bogus-key-in-run-when
  (let [project-spec
        (-> "
            jobs:
              job1:
                run_when:
                  'branch master has been updated':
                    type: branch
                    bogus: whatever
                    include_match: ^master$
            "
            yaml/parse-string
            )]
    (is (thrown-with-msg?
          ValidationException #".*Unknown Property.*"
          (project-configuration-validator/validate! project-spec)))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
