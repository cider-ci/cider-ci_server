; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.storage.shared
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clojure.java.jdbc :as jdbc]
    [me.raynes.fs :as fsutils]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))


;### new helper ###############################################################

(defn find-store [request]
  (let [prefix (-> request :route-params :prefix)]
    (I>> identity-with-logging
         (get-config)
         :services :server :stores
         (filter #(= (str "/" prefix) (:url_path_prefix %)))
         first)))

(defn get-table-and-id-name [request]
  (let [prefix (-> request :route-params :prefix)]
    (case prefix
      "tree-attachments" ["tree_attachments" "tree_id"]
      "trial-attachments" ["trial_attachments" "trial_id"])))

(defn get-row [request]
  (let [path (-> request :route-params :*)
        id (-> request :route-params :id)
        [table-name id-name] (get-table-and-id-name request)
        query [(str "SELECT * FROM " table-name
                    "  WHERE " id-name " = ? AND path = ?") id path]]
    (-> (jdbc/query (get-ds) query)
        first)))


;##############################################################################

(defn delete-file [path]
  (logging/debug delete-file [path])
  (catcher/snatch {}
    (fsutils/delete path)))

(defn delete-row [table id]
  (logging/debug delete-row [table id])
  (catcher/snatch {}
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

