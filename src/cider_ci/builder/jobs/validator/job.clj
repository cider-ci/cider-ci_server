(ns cider-ci.builder.jobs.validator.job
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.jobs.validator.context :refer [validate-context!]]
    [cider-ci.builder.jobs.validator.shared :refer :all]

    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [clojure.data.json :as json]


    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))

(def job-meta-spec
  {
   :context {:validator validate-context!}
   :depends_on nil ; is validated in the configuration validator
   :description {:validator validate-string!}
   :key {:validator validate-string!}
   :name {:validator validate-string!}
   :priority {:validator validate-integer!}
   :run_when nil ;is validated in the configuration validator
   })

(defn validate!
  ([job]
   (validate!
     job (->> ["SELECT * FROM job_specifications WHERE id = ?"
               (:job_specification_id job)]
              (jdbc/query (rdbms/get-ds))
              first :data)))
  ([job job-spec]
   (validate-spec-map! job-spec job-meta-spec [(:key job-spec)])
   job-spec))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
