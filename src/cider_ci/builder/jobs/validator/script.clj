(ns cider-ci.builder.jobs.validator.script
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]

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
   :start_when nil
   :template_environment_variables {:validator validate-boolean!}
   :terminate_when nil
   :timeout {:validator validate-duration!}
   })

(defn validate-script! [script-spec chain]
  (validate-accepted-keys! script-spec script-meta-spec chain)
  (validate-values! script-spec script-meta-spec chain))

;(validate-script! {:template_environment_variables nil})

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)

