(ns cider-ci.builder.jobs.validator.script-dependency
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]
    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci.builder ValidationException]
    ))

(def VALID-SCRIPT-STATES
  #{"aborted"
    "defective"
    "executing"
    "failed"
    "passed"
    "pending"
    "skipped"
    "waiting"})

(defn validate-states! [states chain]
  (when-not (every? VALID-SCRIPT-STATES states)
    (->> {:type "error"
          :description (str (-> states sort format-coll) " in " (format-chain chain)
                            " contains an illegal state. Valid states are: "
                            (-> VALID-SCRIPT-STATES sort format-coll) "."
                            )}
         (ValidationException. "Illegal Script State")
         throw)))

(def script-dependency-meta-spec
  {:script_key {:validator validate-string!
                :required true}
   :states {:validator validate-states!}})

(defn validate-script-dependency! [script-dependency chain]
  (validate-defaults! script-dependency script-dependency-meta-spec chain))

