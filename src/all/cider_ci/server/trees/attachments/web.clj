; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.trees.attachments.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [presence keyword str]])
  (:require

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.ring :refer [wrap-canonicalize-query-params]]

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
    ;[ring.mock.request :as ring-mock]
    ))


(defn tree-attachments-query [tree-id]
  (-> (sql/select :*)
      (sql/from :tree-attachments)
      (sql/merge-where [:= :tree-id tree-id])
      sql/format))

(defn tree-attachments [tree-id]
  {:body (->> tree-id
              tree-attachments-query
              (jdbc/query (rdbms/get-ds)))})

(def routes
  (-> (cpj/routes
        (cpj/GET "/tree-attachments/:tree-id/" [tree-id] (tree-attachments tree-id)))
      wrap-canonicalize-query-params
      (authorize/wrap-require! {:user true})))


;(-> (ring-mock/request :get "/tree-attachments/f4bcaa32890f7315fb55462b890d302e8eb01589/") routes)


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
