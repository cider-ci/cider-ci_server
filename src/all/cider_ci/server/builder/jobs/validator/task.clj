(ns cider-ci.server.builder.jobs.validator.task
  (:refer-clojure :exclude [get str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.jobs.validator.script :refer [validate-script!]]
    [cider-ci.server.builder.jobs.validator.ports :refer [validate-port!]]
    [cider-ci.server.builder.jobs.validator.attachment :refer [validate-attachment!]]
    [cider-ci.server.builder.jobs.validator.git-options :refer [validate-git-options!]]
    [cider-ci.server.builder.jobs.validator.template :refer [validate-template!]]
    [cider-ci.server.builder.jobs.validator.shared :refer :all]

    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))

(defn validate-aggregate-state! [value chain]
  (validate-string! value chain)
  (let [legal-values ["satisfy-any" "satisfy-last"]]
    (when-not (some #{value} legal-values)
      (->> {:type "error"
            :description (str "The value in " (format-chain chain) " is _\"" value
                              "\"_. But it must be any of
                              "(format-coll legal-values) ".")}
           (ValidationException. "Illegal Value")
           throw))))

(defn validate-load-value! [value chain]
  (validate-number! value chain)
  (when-not (> value 0)
    (->> {:type "error"
          :description "The value of `load` must be strictly positive."}
         (ValidationException. "Illegal Value")
         throw)))

(def task-meta-spec
  {:aggregate_state {:validator validate-aggregate-state!}
   :description {:validator validate-string!}
   :dispatch_storm_delay_duration {:validator validate-duration!}
   :eager_trials {:validator validate-integer!}
   :environment_variables {:validator (build-map-of-validator validate-string!)}
   :exclusive_global_resources {:validator (build-map-of-validator validate-boolean!)}
   :git_options {:validator validate-git-options!}
   :key {:validator validate-string!}
   :load {:validator validate-load-value!}
   :max_trials {:validator validate-integer!}
   :name {:validator validate-string!}
   :ports {:validator (build-map-of-validator validate-port!)}
   :priority {:validator validate-integer!}
   :script_defaults {:validator validate-script!}
   :scripts {:validator (build-map-of-validator validate-script!)}
   :templates {:validator (build-map-of-validator validate-template!)}
   :traits {:validator (build-map-of-validator validate-boolean!)}
   :tree_attachments {:validator (build-map-of-validator validate-attachment!)}
   :trial_attachments {:validator (build-map-of-validator validate-attachment!)}})

(defn validate-task! [task chain]
  (validate-spec-map! task task-meta-spec chain))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
