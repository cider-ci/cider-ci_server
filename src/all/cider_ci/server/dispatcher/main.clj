; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.dispatcher.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:gen-class)
  (:require
    [cider-ci.server.dispatcher.result]
    [cider-ci.server.dispatcher.abort :as abort]
    [cider-ci.server.dispatcher.dispatch :as dispatch]
    [cider-ci.server.dispatcher.dispatch.timeout-sweeper]
    [cider-ci.server.dispatcher.task :as task]
    [cider-ci.server.dispatcher.web :as web]
    [cider-ci.server.dispatcher.dispatch.timeout-sweeper :as timeout-sweeper]

    [cider-ci.utils.app]
    [cider-ci.utils.config :refer [get-config]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    ))


(defn initialize []
  (task/initialize)
  (abort/initialize)
  (cider-ci.server.dispatcher.result/initialize)
  (timeout-sweeper/initialize))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [_] (System/exit -1))}
    (cider-ci.utils.app/init web/build-main-handler)
    (initialize)))

