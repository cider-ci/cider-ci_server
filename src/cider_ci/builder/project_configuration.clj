; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.project-configuration
  (:require
    [cider-ci.builder.jobs.validator.project-configuration :as project-configuration-validator]

    [cider-ci.repository]

    [cider-ci.utils.http :as http]
    [cider-ci.utils.config :refer [get-config]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [clojure.data.json :as json]
    ))

(defn get-project-configuration [tree-id]
  (-> tree-id
      cider-ci.repository/project-configuration
      project-configuration-validator/validate!
      ))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(def ^:dynamic *caching-enabled* false)
;(debug/debug-ns *ns*)
