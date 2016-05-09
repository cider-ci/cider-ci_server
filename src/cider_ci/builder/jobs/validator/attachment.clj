(ns cider-ci.builder.jobs.validator.attachment
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

(def attachment-meta-spec
  {:content_type {:validator validate-string!
                  :required true }
   :include_match {:validator validate-string!
                   :required true }
   :exclude_match {:validator validate-string!}})

(defn validate-attachment! [port chain]
  (validate-accepted-keys! port attachment-meta-spec chain)
  (validate-values! port attachment-meta-spec chain)
  (validate-required-keys! port attachment-meta-spec chain))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
