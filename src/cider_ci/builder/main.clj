; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:gen-class)
  (:require
    [cider-ci.utils.app]
    [cider-ci.utils.config :refer [get-config]]

    [cider-ci.builder.evaluation :as evaluation]
    [cider-ci.builder.trials :as trials]
    [cider-ci.builder.retention-sweeper :as retention-sweeper]
    [cider-ci.builder.jobs.trigger :as jobs.trigger]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.web :as web]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    ))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn #(System/exit -1)}
    (cider-ci.utils.app/init web/build-main-handler)
    (tasks/initialize)
    (trials/initialize)
    (evaluation/initialize)
    (jobs.trigger/initialize)
    (retention-sweeper/initialize)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(require (ns-name *ns*) :reload-all)
;(debug/debug-ns *ns*)
