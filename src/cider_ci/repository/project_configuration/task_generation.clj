; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.project-configuration.task-generation
  (:require
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.repository :as sql.repository]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.tools.logging :as logging]
    [clojure.walk :refer [keywordize-keys]]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))



;##############################################################################

(defn- file-name-to-task [file-name]
  [file-name {:environment-variables
              {:CIDER_CI_TASK_FILE file-name}}])

(defn generate-tasks [git-ref-id context]
  (if-let [generate-spec (:_cider-ci_generate-tasks context)]
    (let [repository (sql.repository/resolve git-ref-id)
          include-regex (-> generate-spec :include-match)
          exclude-regex (-> generate-spec :exclude-match)
          file-list (git.repositories/ls-tree
                      repository git-ref-id include-regex exclude-regex)
          generated-tasks (->> file-list
                               (map file-name-to-task)
                               (into {})
                               clojure.walk/keywordize-keys)
          tasks (if-let [existing-tasks (:tasks context)]
                  (cond (map? existing-tasks) (deep-merge
                                                generated-tasks existing-tasks)
                        :else (throw (IllegalStateException.
                                       (str "tasks must be a map"
                                            " to be merged with generated-tasks"))))
                  generated-tasks)]
      (-> context
          (assoc :tasks tasks)
          (dissoc :_cider-ci_generate-tasks)))
    context))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
