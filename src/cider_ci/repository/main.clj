; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:gen-class)
  (:require
    [cider-ci.repository.constants :refer :all]
    [cider-ci.repository.branch-updates.core :as branch-updates]
    [cider-ci.repository.fetch-and-update.core :as fetch-and-update]
    [cider-ci.repository.push-hooks.core :as push-hooks]
    [cider-ci.repository.status-pushes.core :as status-pushes]
    [cider-ci.repository.sweeper :as sweeper]

    [cider-ci.repository.state :as state]
    [cider-ci.repository.web :as web]
    [cider-ci.repository.web.push]

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
    (state/initialize)
    (cider-ci.repository.web.push/initialize)
    (assert-proper-context!)
    (branch-updates/initialize)
    (fetch-and-update/initialize)
    (push-hooks/initialize)
    (status-pushes/initialize)
    (sweeper/initialize)))
