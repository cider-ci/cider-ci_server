; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.main
  (:gen-class)
  (:require
    [cider-ci.self]
    [cider-ci.utils.app]
    [cider-ci.repository.web :as web]
    [cider-ci.repository.repositories :as repositories]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    ))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn #(System/exit -1)}
    (cider-ci.utils.app/init web/build-main-handler)
    (repositories/initialize)))
