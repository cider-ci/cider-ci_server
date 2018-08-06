; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:gen-class)
  (:require
    [cider-ci.server.repository.constants :refer :all]
    [cider-ci.server.repository.branch-updates.core :as branch-updates]
    [cider-ci.server.repository.fetch-and-update.core :as fetch-and-update]
    [cider-ci.server.repository.push-hooks.core :as push-hooks]
    [cider-ci.server.repository.status-pushes.core :as status-pushes]
    [cider-ci.server.repository.sweeper :as sweeper]

    [cider-ci.server.repository.state :as state]
    [cider-ci.server.repository.web :as web]
    [cider-ci.server.repository.web.push]

    ;[cider-ci.utils.app]
    [cider-ci.utils.config :refer [get-config]]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    ))

(defn initialize []
  (state/initialize)
  (cider-ci.server.repository.web.push/initialize)
  (branch-updates/initialize)
  (fetch-and-update/initialize)
  (push-hooks/initialize)
  (status-pushes/initialize)
  (sweeper/initialize))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    ;(cider-ci.utils.app/init web/build-main-handler)
    (initialize)))
