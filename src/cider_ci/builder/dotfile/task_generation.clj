; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile.task-generation
  (:require 
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.util :as util :refer [deep-merge]]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.tools.logging :as logging]
    [clojure.walk :refer [keywordize-keys]]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))



;##############################################################################

(defn file-name-to-task [file-name]
  [file-name {:environment-variables
              {:CIDER_CI_TASK_FILE file-name}}])

(defn generate-tasks [git-ref-id context]
  (if-let [generate-spec (:_cider-ci_generate-tasks context)]
    (let [file-list (repository/ls-tree git-ref-id generate-spec)
          generated-tasks (->> file-list
                               (map file-name-to-task) 
                               (into {})
                               clojure.walk/keywordize-keys)
          tasks (if-let [existing-tasks (:tasks context)]
                  (cond (map? existing-tasks) (deep-merge 
                                                generated-tasks existing-tasks)
                        :else (throw (IllegalStateException. 
                                       "tasks must be a map to be merged with generated-tasks")))
                  generated-tasks)] 
      (-> context
          (assoc :tasks tasks)
          (dissoc :_cider-ci_generate-tasks)))
    context))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
