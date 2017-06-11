; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.sweeper
  (:require
    [cider-ci.server.repository.shared :refer [repositories-fs-base-path]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.nio :as nio]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]
    [clojure.java.jdbc :as jdbc]
    )
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))



(defn existing-file-repos []
  "List of all existing directories underneath the
  path where we store the repositories."
  (with-open [dir-repos  (-> (repositories-fs-base-path) nio/path nio/dir-stream)]
    (->> dir-repos
         (filter nio/dir?)
         (map #(-> % nio/split last str))
         set)))

(defn db-repos []
  "List of all known directories by the database."
  (->> ["SELECT id FROM repositories"]
       (jdbc/query (rdbms/get-ds))
       (map :id)
       (map str)
       set))

(defn delete-orphans []
  (let [file-repos (existing-file-repos)
        known-repos (db-repos)
        to-be-deleted-repos (clojure.set/difference file-repos known-repos)]
    (doseq [repo-id to-be-deleted-repos]
      (let [path (str (repositories-fs-base-path) "/" repo-id)]
        (logging/info "deleting orphaned repository " path)
        (catcher/snatch {} (system/exec! ["rm" "-rf" path]))))))

(defdaemon "delete-orphans" 60 (delete-orphans))


;### initialize ###############################################################

(defn initialize []
  (start-delete-orphans))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)


