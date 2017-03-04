(ns cider-ci.builder.jobs.validator.template
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]
    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))

(def template-meta-spec
  {:src {:validator validate-string!
         :required true }
   :dest {:validator validate-string!
          :required true }})

(defn validate-template! [port chain]
  (validate-spec-map! port template-meta-spec chain))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
