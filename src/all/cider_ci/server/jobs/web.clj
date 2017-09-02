; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.jobs.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [presence keyword str]])
  (:require

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.ring :as ring-utils]

    [cheshire.core]
    [compojure.core :as cpj]
    [cider-ci.utils.honeysql :as sql]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(def jobs-base-query
  (-> (sql/select :jobs.*)
      (sql/from :jobs)))

(defn filter-by-tree-id [query {{tree-id :tree-id} :query-params}]
  (if tree-id
    (-> query (sql/merge-where [:= :jobs.tree-id tree-id]))
    query))

(defn jobs [request]
  {:body (->> (-> jobs-base-query
                  (filter-by-tree-id request)
                  sql/format)
              (jdbc/query (rdbms/get-ds))
              (map (fn [r] [(:id r) r]))
              (into {}))})

(def routes
  (ring-utils/wrap-canonicalize-query-params
    (cpj/routes
      (cpj/GET  "/jobs/" _
               (authorize/wrap-require! #'jobs {:user true})))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)
