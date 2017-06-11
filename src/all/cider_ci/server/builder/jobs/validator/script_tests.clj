(ns cider-ci.server.builder.jobs.validator.script-tests
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

(deftest test-validate!
  (testing "accepted keys"
    (let [job-spec-with-a-bogus-key-in-a-script
          (-> "
              key: job-key
              name: job-name
              tasks:
                task1:
                  scripts:
                    script1:
                      bogus: whatever
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (testing job-spec-with-a-bogus-key-in-a-script
        (is (thrown-with-msg?
              ValidationException #"Validation Error - Unknown Property"
              (validate! nil job-spec-with-a-bogus-key-in-a-script)))
        (try (validate! nil job-spec-with-a-bogus-key-in-a-script)
             (catch ValidationException e
               (is (= (.getMessage e) "Validation Error - Unknown Property"))
               (let [data (ex-data e)]
                 (logging/debug data)
                 (is (re-matches
                       #"^.*job-key.*context.*tasks.*task1.*scripts.*script1.*unknown property.*bogus.*$"
                       (:description data))))))))

    (let [job-spec-with-a-bogus-key-in-script-defaults
          (-> "
              key: job-key
              name: job-name
              context:
                script_defaults:
                  bogus: whatever
              "
              yaml/parse-string
              normalize-job-spec
              )]
      (testing job-spec-with-a-bogus-key-in-script-defaults
        (is (thrown-with-msg?
              ValidationException #"Validation Error - Unknown Property"
              (validate! nil job-spec-with-a-bogus-key-in-script-defaults)))
        (try (validate! nil job-spec-with-a-bogus-key-in-script-defaults)
             (catch ValidationException e
               (is (= (.getMessage e) "Validation Error - Unknown Property"))
               (let [data (ex-data e)]
                 (logging/debug data)
                 (is (re-matches
                       #"^.*job-key.*context.*script_defaults.*unknown property.*bogus.*$"
                       (:description data))))))))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
