; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.main
  (:gen-class)
  (:require
    [cider-ci.self]

    [cider-ci.dispatcher.result]
    [cider-ci.dispatcher.abort :as abort]
    [cider-ci.dispatcher.dispatch :as dispatch]
    [cider-ci.dispatcher.dispatch.timeout-sweeper]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.web :as web]
    [cider-ci.dispatcher.dispatch.timeout-sweeper :as timeout-sweeper]

    [cider-ci.utils.app]
    [cider-ci.utils.config :refer [get-config]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    ))


(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [_] (System/exit -1))}
    (cider-ci.utils.app/init web/build-main-handler)

    (task/initialize)
    (abort/initialize)
    (cider-ci.dispatcher.result/initialize)
    (timeout-sweeper/initialize)

    ))

