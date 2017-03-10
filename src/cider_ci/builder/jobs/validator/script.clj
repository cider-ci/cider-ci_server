(ns cider-ci.builder.jobs.validator.script
  (:refer-clojure :exclude [get str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]
    [cider-ci.builder.jobs.validator.script-dependency :refer [validate-script-dependency!]]

    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))

(def script-meta-spec
  {:body {:validator validate-string!}
   :description {:validator validate-string!}
   :environment_variables {:validator (build-map-of-validator validate-string!)}
   :exclusive_executor_resource {:validator validate-string!}
   :ignore_abort {:validator validate-boolean!}
   :ignore_state {:validator validate-boolean!}
   :key {:validator validate-string!}
   :name {:validator validate-string!}
   :start_when {:validator (build-map-of-validator validate-script-dependency!)}
   :template_environment_variables {:validator validate-boolean!}
   :terminate_when {:validator (build-map-of-validator validate-script-dependency!)}
   :timeout {:validator validate-duration!}
   })

(defn validate-script! [script-spec chain]
  (validate-spec-map! script-spec script-meta-spec chain))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)

