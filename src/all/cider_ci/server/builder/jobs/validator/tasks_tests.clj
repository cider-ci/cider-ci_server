(ns cider-ci.server.builder.jobs.validator.tasks-tests
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

(def job-spec-with-a-bogus-key-in-a-task
  (-> "
      key: job-key
      name: job-name
      context:
        tasks:
          task1:
            bogus: whatever
      "
      yaml/parse-string
      normalize-job-spec
      ))

(deftest test-validate!
  (testing job-spec-with-a-bogus-key-in-a-task
    (is (thrown-with-msg?
          ValidationException #"Validation Error - Unknown Property"
          (validate! nil job-spec-with-a-bogus-key-in-a-task)))
    (try (validate! nil job-spec-with-a-bogus-key-in-a-task)
         (catch ValidationException e
           (is (= (.getMessage e) "Validation Error - Unknown Property"))
           (let [data (ex-data e)]
             (logging/debug data)
             (is (re-matches
                   #"^.*job-key.*context.*tasks.*task1.*unknown property.*bogus.*$"
                   (:description data))))))))

(def job-spec-with-a-bogus-key-in-a-task-defaults
  (-> "
      key: job-key
      name: job-name
      context:
        task_defaults:
          bogus: whatever
      "
      yaml/parse-string
      normalize-job-spec
      ))

(deftest test-validate!
  (testing job-spec-with-a-bogus-key-in-a-task-defaults
    (is (thrown-with-msg?
          ValidationException #"Validation Error - Unknown Property"
          (validate! nil job-spec-with-a-bogus-key-in-a-task-defaults)))
    (try (validate! nil job-spec-with-a-bogus-key-in-a-task-defaults)
         (catch ValidationException e
           (is (= (.getMessage e) "Validation Error - Unknown Property"))
           (let [data (ex-data e)]
             (logging/debug data)
             (is (re-matches
                   #"^.*job-key.*context.*task_defaults.*unknown property.*bogus.*$"
                   (:description data))))))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
