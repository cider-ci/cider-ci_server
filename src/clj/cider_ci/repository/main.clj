; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.main
  (:gen-class)
  (:require
    [cider-ci.repository.constants :refer :all]

    [cider-ci.repository.notifications.core :as notifications]
    [cider-ci.repository.repositories.fetch-scheduler :as fetch-scheduler]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.web :as web]

    [cider-ci.utils.app]
    [cider-ci.utils.config :refer [get-config]]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    ))

(defn assert-proper-context! []
  (let [defined-context (str CONTEXT)
        configured-context (->> [:context :sub_context]
                                (map #(-> (get-config) :services
                                          :repository :http (get %)))
                                clojure.string/join)]
    (assert (= defined-context configured-context))))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    (cider-ci.utils.app/init web/build-main-handler)
    (assert-proper-context!)
    (state/initialize)
    (fetch-scheduler/initialize)
    (notifications/initialize)
    ))
