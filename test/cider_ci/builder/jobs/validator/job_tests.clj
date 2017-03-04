(ns cider-ci.builder.jobs.validator.job-tests
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

(def job-spec-with-bogus-key
  (-> "
      key: job-with-bogus-key
      name: job-with-bogus-key
      bogus: whatever
      "
      yaml/parse-string
      normalize-job-spec
      ))

(deftest test-validate!
  (testing "A bogus key in the top level spec"
    (is (thrown-with-msg?
          ValidationException #"Validation Error - Unknown Property"
          (validate! nil job-spec-with-bogus-key)))
    (try (validate! nil job-spec-with-bogus-key)
         (catch ValidationException e
           (is (= (.getMessage e) "Validation Error - Unknown Property"))
           (let [data (ex-data e)]
             (logging/debug data)
             (is (re-matches
                   #"^.*job-with-bogus-key.*unknown property.*bogus.*$"
                   (:description data))))))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
