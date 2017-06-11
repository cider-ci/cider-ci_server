(ns cider-ci.server.builder.jobs.validator.script-dependency
  (:refer-clojure :exclude [get str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.jobs.validator.shared :refer :all]
    [cider-ci.constants]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))

(def script-states
  (-> cider-ci.constants/STATES :SCRIPT set))

(defn validate-script-states! [states chain]
  (validate-states!
    states script-states  chain))

(def script-dependency-meta-spec
  {:script_key {:validator validate-string!
                :required true}
   :states {:validator validate-script-states!}})

(defn validate-script-dependency! [script-dependency chain]
  (validate-spec-map! script-dependency script-dependency-meta-spec chain))

