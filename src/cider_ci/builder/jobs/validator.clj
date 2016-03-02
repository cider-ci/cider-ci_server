(ns cider-ci.builder.jobs.validator
  (:require
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(def accepted-keys [
                    :context
                    :depends_on
                    :description
                    :empty_tasks_warning
                    :run_on
                    ])

(defn validate!
  ([job]
   (validate!
     job (->> ["SELECT * FROM job_specifications WHERE id = ?"
               (:job_specification_id job)]
              (jdbc/query (rdbms/get-ds))
              first)))

  ([job job-spec]
   ))

;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
