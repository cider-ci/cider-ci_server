(ns cider-ci.builder.jobs.validator.ports
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

(def ports-meta-spec
  {:max {:validator validate-integer!
         :required true }
   :min {:validator validate-integer!
         :required true}})

(defn validate-range! [port chain]
  (when-not (> (:max port) (:min port))
    (->> {:type "error"
          :description (str "The max value must be strictly greater than
                            the min value in " (format-chain chain) "."
                            )}
         (ValidationException. "Invalid Port Range")
         throw)
    ))

(defn validate-port! [port chain]
  (validate-defaults! port ports-meta-spec chain)
  (validate-range! port chain))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
