(ns cider-ci.builder.jobs.validator.project-configuration
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]

    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(def project-config-meta-spec
  {:jobs nil
   :name {:validator validate-string!}
   :description {:validator validate-string!}
   })


(defn validate! [project-config]
  (validate-defaults!
    project-config project-config-meta-spec ["project-configuration"]))
