; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.sweeper
  (:require 
    [cider-ci.utils.daemon :as daemon]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fsutils]
    )
  (:use 
    [cider-ci.storage.shared :only [delete-file delete-row delete-file-and-row]]
    ))


(defonce conf (atom {}))

;##############################################################################

(defn delete-expired []
  (doseq [store @conf]
    (logging/info "cleaning rows without to_be_retained_before not set in " store)
    (doseq [file-row (jdbc/query (rdbms/get-ds) [ (str "SELECT * FROM " (:db_table store)
                                                    " WHERE to_be_retained_before IS NULL "
                                                    " AND created_at < now() - interval '"
                                                    (:retention_time_days store) "  Day' ")])]
      (delete-file-and-row store file-row))
    (logging/info "cleaning rows without to_be_retained_before set in " store)
    (doseq [file-row (jdbc/query (rdbms/get-ds) [ (str "SELECT * FROM " (:db_table store)
                                                   " WHERE to_be_retained_before IS NOT NULL "
                                                   " AND to_be_retained_before < now() ")])]
      (delete-file-and-row store file-row))))

(defn string-is-uuid? [string]
  (re-matches #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" string))

(defn delete-file-orphans []
  (logging/debug delete-file-orphans [])
  (doseq [store  @conf]
    (doseq [file (.listFiles (io/file (:file_path store)))]
      (logging/debug file)
      (catcher/wrap-with-suppress-and-log-error
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

(daemon/define "delete-expired" start-delete-expired stop-delete-expired (* 5 60)
  (logging/info "Deleting expired ...")
  (delete-expired))

(daemon/define "delete-orphans" start-delete-orphans stop-delete-orphans (* 20 60 60)
  (logging/info "Deleting orphans ...")
  (delete-row-orphans)
  (delete-file-orphans))


;### initialize ###############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-delete-expired)
  (start-delete-orphans))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


