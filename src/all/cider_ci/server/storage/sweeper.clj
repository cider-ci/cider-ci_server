; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.storage.sweeper
  (:require
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]

    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [me.raynes.fs :as fsutils]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:use
    [cider-ci.server.storage.shared :only [delete-file delete-row delete-file-and-row]]
    ))


(defonce conf (atom {}))

;##############################################################################

(defn string-is-uuid? [string]
  (re-matches #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" string))

(defn delete-file-orphans []
  (logging/debug delete-file-orphans [])
  (doseq [store  @conf]
    (doseq [file (.listFiles (io/file (:file_path store)))]
      (logging/debug file)
      (catcher/snatch {}
        (let [abs-path (.getAbsolutePath file)
              file-name (fsutils/name file)]
              (logging/debug "checking" {:abs-path abs-path :file-name file-name})
              (when (.exists file)
                (when (.isFile file)
                  (if-not (string-is-uuid? file-name)
                    (delete-file abs-path)
                    (if-not (first (jdbc/query (rdbms/get-ds)
                                               [ (str "SELECT * FROM " (:db_table store)
                                                      " WHERE id = ?::uuid " ) file-name]))
                      (delete-file abs-path))))
                (when (.isDirectory file)
                  (fsutils/delete-dir abs-path))))))))

(defn delete-row-orphans []
  (logging/debug delete-row-orphans [])
  (doseq [store @conf]
    (let [table-name (:db_table store)]
      (doseq [{id :id} (jdbc/query (rdbms/get-ds) [(str "SELECT id FROM " table-name)])]
        (logging/debug "check if row is orphan " (str id))
        (when-not (fsutils/exists? (str (:file_path store) "/" id))
          (logging/debug "file not found, deleting row ...")
          (delete-row table-name id))))))

(defdaemon "delete-orphans" (* 20 60 60)
  (logging/info "Deleting orphans ...")
  (delete-row-orphans)
  (delete-file-orphans))


;### initialize ###############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-delete-orphans))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


