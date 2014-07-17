; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.sweeper
  (:require 
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fsutils]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom {}))

(defn delete-file [path]
  (logging/debug delete-file [path])
  (with/suppress-and-log-error
    (fsutils/delete path)))

(defn delete-row [table id]
  (logging/debug delete-row [table id])
  (with/suppress-and-log-error
    (jdbc/delete! (:ds @conf) table ["id = ?::uuid" id])))

(defn delete-file-and-row [store file-row]
  (logging/debug delete-file-and-row [store file-row])
  (let [path (str (:file_path store) "/" (:id file-row))]
    (delete-file path)
    (delete-row (:db_table store) (:id file-row))))

(defn delete-expired []
  (doseq [store (:stores @conf)]
    (logging/info "cleaning rows without to_be_retained_before not set in " store)
    (doseq [file-row (jdbc/query (:ds @conf) [ (str "SELECT * FROM " (:db_table store)
                                                    " WHERE to_be_retained_before IS NULL "
                                                    " AND created_at < now() - interval '"
                                                    (:retention_time_days store) "  Day' ")])]
      (delete-file-and-row store file-row))
    (logging/info "cleaning rows without to_be_retained_before set in " store)
    (doseq [file-row (jdbc/query (:ds @conf) [ (str "SELECT * FROM " (:db_table store)
                                                   " WHERE to_be_retained_before IS NOT NULL "
                                                   " AND to_be_retained_before < now() ")])]
      (delete-file-and-row store file-row))))

(defn string-is-uuid? [string]
  (re-matches #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" string))

;(string-is-uuid? "e96e4ef8-ef49-468b-be28-76ad02c5f911")

(defn delete-file-orphans []
  (logging/debug delete-file-orphans [])
  (doseq [store (:stores @conf)]
    (doseq [file (.listFiles (io/file (:file_path store)))]
      (logging/debug file)
      (with/suppress-and-log-error
        (let [abs-path (.getAbsolutePath file)
              file-name (fsutils/name file)]
              (logging/debug "checking" {:abs-path abs-path :file-name file-name})
              (when (.exists file)
                (when (.isFile file)
                  (if-not (string-is-uuid? file-name)
                    (delete-file abs-path)
                    (if-not (first (jdbc/query (:ds @conf) 
                                               [ (str "SELECT * FROM " (:db_table store)
                                                      " WHERE id = ?::uuid " ) file-name]))
                      (delete-file abs-path))))
                (when (.isDirectory file)
                  (fsutils/delete-dir abs-path))))))))


(defn delete-row-orphans []
  (logging/debug delete-row-orphans [])
  (doseq [store (:stores @conf)]
    (let [table-name (:db_table store)]
      (doseq [{id :id} (jdbc/query (:ds @conf) [(str "SELECT id FROM " table-name)])]
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

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-delete-expired)
  (start-delete-orphans))

