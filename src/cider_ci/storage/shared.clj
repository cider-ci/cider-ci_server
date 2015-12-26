; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.shared
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fsutils]
    ))


;##############################################################################

(defn delete-file [path]
  (logging/debug delete-file [path])
  (catcher/wrap-with-suppress-and-log-error
    (fsutils/delete path)))

(defn delete-row [table id]
  (logging/debug delete-row [table id])
  (catcher/wrap-with-suppress-and-log-error
    (jdbc/delete! (rdbms/get-ds) table ["id = ?::uuid" id])))

(defn delete-file-and-row [store file-row]
  (logging/debug delete-file-and-row [store file-row])
  (let [path (str (:file_path store) "/" (:id file-row))]
    (and (delete-file path)
         (delete-row (:db_table store) (:id file-row)))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

