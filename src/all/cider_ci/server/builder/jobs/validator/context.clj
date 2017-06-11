(ns cider-ci.server.builder.jobs.validator.context
  (:refer-clojure :exclude [get str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.jobs.validator.task :refer [validate-task!]]
    [cider-ci.server.builder.jobs.validator.script :refer [validate-script!]]

    [cider-ci.server.builder.jobs.validator.shared :refer :all]

    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [clojure.set :refer :all]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))

(declare context-meta-spec)

(defn validate-context! [context chain]
  (validate-spec-map! context context-meta-spec chain))

(def context-meta-spec
  {
   :contexts {:validator (build-map-of-validator validate-context!)}
   :generate_tasks nil ; TODO
   :key {:validator validate-string!}
   :name {:validator validate-string!}
   :script_defaults {:validator validate-script!}
   :task_defaults {:validator validate-task!}
   :tasks {:validator (build-map-of-validator validate-task!)}
   })


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
