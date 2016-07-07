(ns cider-ci.builder.jobs.validator.script-dependency
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]
    [cider-ci.utils.core :refer :all]

    [cider-ci.utils.config :refer [get-config]]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci.builder ValidationException]
    ))

(defn validate-script-states! [states chain]
  (validate-states!
    states (-> (get-config) :constants :STATES :SCRIPT set) chain))

(def script-dependency-meta-spec
  {:script_key {:validator validate-string!
                :required true}
   :states {:validator validate-script-states!}})

(defn validate-script-dependency! [script-dependency chain]
  (validate-spec-map! script-dependency script-dependency-meta-spec chain))

