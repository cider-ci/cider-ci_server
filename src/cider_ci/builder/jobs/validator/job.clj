(ns cider-ci.builder.jobs.validator.job
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
   :depends_on nil
   :description nil
   :empty_tasks_warning nil
   :key nil
   :name nil
   :run_on nil
   })

(defn validate!
  ([job]
   (validate!
     job (->> ["SELECT * FROM job_specifications WHERE id = ?"
               (:job_specification_id job)]
              (jdbc/query (rdbms/get-ds))
              first :data)))

  ([job job-spec]
   (validate-accepted-keys! job-spec job-meta-spec [(:key job-spec)])
   (validate-values! job-spec job-meta-spec [(:key job-spec)])


   ;(validate-required-keys! job-spec)
   ;(validate-context! "main" (-> job-spec :data :context))
   job-spec
   ))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
