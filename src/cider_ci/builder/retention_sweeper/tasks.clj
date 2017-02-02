; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.retention-sweeper.tasks
  (:require
    [cider-ci.utils.config :refer [get-config parse-config-duration-to-seconds]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.builder.retention-sweeper.shared :refer [retention-interval]]

    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))
(defn- sweep []
  (catcher/snatch
    {}
    (when-let [interval (retention-interval :task_retention_duration)]
      (->>(-> (sql/delete-from :tasks)
              (sql/merge-where (sql/raw
                                 (str "(tasks.updated_at < (now() - interval'"
                                      interval "'))")))
              (sql/returning :tasks.id)
              sql/format)
              (jdbc/query (get-ds))))))


(defdaemon "sweep" 1 (sweep))

(defn initialize [] (start-sweep))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
