; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.sync.update-executor
  (:require
    [cider-ci.dispatcher.executor :as executor-entity]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]


    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.ring :refer [wrap-handler-with-logging]]

    ))


(defn update-when-changed [executor data]

  (when-not (= (sort (:traits executor)) (sort (:traits data)))
    (jdbc/update!
      (rdbms/get-ds)
      :executors
      {:traits (sort (:traits data))}
      ["id = ?" (:id executor)]))

  (when-not (= (sort (:accepted_repositories executor)) (sort (:accepted_repositories data)))
    (jdbc/update!
      (rdbms/get-ds)
      :executors
      {:accepted_repositories (sort (:accepted_repositories data))}
      ["id = ?" (:id executor)]))

  (when-let [max-load (:max_load data)]
    (when-not (= (:max_load executor) max-load)
      (jdbc/update!
        (rdbms/get-ds)
        :executors
        {:max_load max-load}
        ["id = ?" (:id executor)])))

  )

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(debug/re-apply-last-argument #'update-when-changed)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
