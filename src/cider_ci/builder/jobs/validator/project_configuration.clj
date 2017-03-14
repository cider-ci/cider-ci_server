; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.validator.project-configuration
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.builder.jobs.validator.shared :refer :all]

    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.core :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci ValidationException]
    ))


;### cron trigger #############################################################

(defn validate-cron-value! [spec chain]
  ; TODO
  )

(def cron-dep-meta-spec
  {:type {:required true
          :validator validate-string!}
   :value {:required true
           :validator validate-cron-value!}
   :rerun {:required false
           :validator validate-boolean!}})

(defn validate-cron-dep! [spec chain]
  (validate-spec-map! spec cron-dep-meta-spec chain))


;### branch dependency or trigger #############################################

(def branch-dep-meta-spec
  {:type {:required true
          :validator validate-string!}
   :include_match {:validator validate-string!}
   :exclude_match {:validator validate-string!}
   })

(defn validate-branch-dep! [spec chain]
  (validate-spec-map! spec branch-dep-meta-spec chain)
  )

;### job dependency or trigger ################################################

(defn validate-job-states! [states chain]
  (validate-states!
    states (-> (get-config) :constants :STATES :JOB set) chain))

(def job-dep-meta-spec
  {:type {:required true
          :validator validate-string!}
   :job_key {:required true
             :validator validate-string!}
   :submodule {:validator (build-collection-of-validator validate-string!)}
   :states {:required true
            :validator validate-job-states!  }
   })


(defn validate-job-dep! [spec chain]
  (validate-spec-map! spec job-dep-meta-spec chain))

;##############################################################################

(defn validate-dependency-or-trigger! [spec chain]
  (case (:type spec)
    "cron" (validate-cron-dep! spec chain)
    "job" (validate-job-dep! spec chain)
    "branch" (validate-branch-dep! spec chain)
    (->> {:type "error"
          :description
          (str "The type _\"" (:type spec) "\"_ in " (format-chain chain)
               " must be either _\"cron\"_, _\"branch\"_, or _\"job\"_." )}
         (ValidationException. "Invalide Type")
         throw)))

;##############################################################################

(def job-config-meta-spec
  {:context nil ; will be validated in the job spec
   :depends_on {:validator (build-map-of-validator validate-dependency-or-trigger!)}
   :description {:validator validate-string!}
   :generate_tasks nil ; part of the compact notation
   :key {:validator validate-string!}
   :name {:validator validate-string!}
   :priority {:validator validate-integer!}
   :run_when {:validator (build-map-of-validator validate-dependency-or-trigger!)}
   :task nil ; part of the compact notation
   :task_defaults nil ; part of the compact notation
   :tasks nil ; part of the compact notation
   })

(defn validate-job! [job-spec chain]
  (validate-spec-map!
    job-spec job-config-meta-spec chain))


;##############################################################################

(def project-config-meta-spec
  {:jobs {:validator (build-map-of-validator validate-job!)
          :required true }
   :name {:validator validate-string!}
   :description {:validator validate-string!}
   })

(defn validate! [project-config]
  (catcher/with-logging
    {:level :debug}
    (validate-spec-map!
      project-config project-config-meta-spec ["project-configuration"])
    project-config))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
