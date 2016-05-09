(ns cider-ci.builder.jobs.validator.git-options
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

(def submodules-meta-spec
  {:include_match {:validator validate-string!}
   :exclude_match {:validator validate-string!}})

(defn validate-either-macher! [submodules chain]
  (when-not (or (:include_match submodules)
                (:exclude_match submodules))
    (->> {:type "error"
          :description (str "The git-options/submodules map in "
                            (format-chain chain) " must contain either a "
                            "include_match or exclude_match property.")}
         (ValidationException. "Required Property Missing")
         throw)))

(defn validate-submodules! [submodules chain]
  (validate-defaults! submodules submodules-meta-spec chain)
  (validate-either-macher! submodules chain))

(def git-options-meta-spec
  {:submodules {:validator validate-submodules!
                :required true }})

(defn validate-git-options! [git-options chain]
  (validate-defaults! git-options git-options-meta-spec chain))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
