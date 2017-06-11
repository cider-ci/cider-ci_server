; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.main]
    [cider-ci.server.dispatcher.main]
    [cider-ci.server.repository.main]
    [cider-ci.server.storage.main]
    [cider-ci.server.executors]
    [cider-ci.server.web :as web]
    [cider-ci.server.state]
    [cider-ci.server.push]
    [cider-ci.utils.app :as app]

    [logbug.catcher :as catcher]
    ))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    (app/init :server web/build-main-handler)
    (cider-ci.server.repository.main/initialize)
    (cider-ci.server.builder.main/initialize)
    (cider-ci.server.dispatcher.main/initialize)
    (cider-ci.server.storage.main/initialize)
    (cider-ci.server.executors/initialize)
    (cider-ci.server.state/initialize)
    (cider-ci.server.push/initialize)
    ))



