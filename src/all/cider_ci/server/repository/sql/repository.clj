; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.sql.repository
  (:refer-clojure :exclude [str keyword resolve])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn resolve
  "Returns a repository given a sha1 commit-id or tree-id."
  [id]
  (first (jdbc/query (rdbms/get-ds)
                     [(str " SELECT repositories.* FROM repositories"
                           " JOIN branches ON branches.repository_id = repositories.id"
                           " JOIN branches_commits ON branches_commits.branch_id = branches.id"
                           " JOIN commits ON commits.id = branches_commits.commit_id"
                           " WHERE (commits.id = ? OR commits.tree_id = ?)") id id])))

(defn get-repository-by-update-notification-token [token]
  (catcher/snatch {}
    (->> (jdbc/query (rdbms/get-ds)
                     ["SELECT * from repositories WHERE update_notification_token = ?"
                      token]) first )))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
