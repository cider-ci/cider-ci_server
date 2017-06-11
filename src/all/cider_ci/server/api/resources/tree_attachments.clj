; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.resources.tree-attachments
  (:require
    [cider-ci.server.api.pagination :as pagination]
    [cider-ci.server.api.util :as util]
    [logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [honeysql.sql :refer :all]
    [ring.util.response :as response]
    ))


;### get-attachments ############################################################
(defn build-attachments-base-query [tree-id]
  (-> (sql-from :tree_attachments)
      (sql-select :id :path)
      (sql-where [:= :tree_id tree-id])
      (sql-order-by [:path :asc])))

(defn filter-by-path-segment [query tree-id query-params]
  (if-let [pathsegment (:pathsegment query-params)]
    (-> query
        (sql-merge-where [:like :path (str "%" pathsegment "%")]))
    query))

(defn attachments-data [job-id query-params]
  (let [tree-id (:tree_id
                   (first (jdbc/query
                            (rdbms/get-ds)
                            ["SELECT tree_id FROM jobs WHERE id = ?"
                             job-id])))
        query (-> (build-attachments-base-query tree-id)
                  (filter-by-path-segment tree-id query-params)
                  (pagination/add-offset-for-honeysql  query-params)
                  sql-format)]
    (logging/debug {:query query})
    (jdbc/query (rdbms/get-ds) query)))

(defn get-attachments [request]
  (let [job-id (-> request :route-params :job_id)
        query-params (-> request :query-params)]
    {:body
     {:tree_attachments
      (attachments-data job-id query-params)}}))

;### routes #####################################################################
(def routes
  (cpj/routes
    (cpj/GET "/jobs/:job_id/tree-attachments/" request (get-attachments request))
    ))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
