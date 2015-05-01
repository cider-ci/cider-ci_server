; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.main
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.builder.jobs.trigger :as jobs.trigger]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.tasks :as tasks]
    [cider-ci.builder.web :as web]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.thrown]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [pg-types.all]
    ))


(defn -main [& args]
  (catcher/wrap-with-log-error
    (drtom.logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
    (config/initialize)
    (rdbms/initialize (get-db-spec :builder))
    (nrepl/initialize (-> (get-config) :services :builder :nrepl))
    (messaging/initialize (:messaging (get-config)))
    (tasks/initialize)
    (auth/initialize (select-keys (get-config) [:secret :session :basic_auth]))
    (web/initialize)
    (jobs.trigger/initialize)
    (http/initialize (get-config))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(require (ns-name *ns*) :reload-all)
;(debug/debug-ns *ns*)
