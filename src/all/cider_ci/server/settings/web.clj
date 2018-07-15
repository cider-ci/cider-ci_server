; Copyright © 2017 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.settings.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [presence keyword str deep-merge]])
  (:require

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.ring :refer [wrap-canonicalize-query-params]]

    [cheshire.core]
    [compojure.core :as cpj]
    [cider-ci.utils.honeysql :as sql]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn get-settings [request]
  {:body
   (-> (jdbc/query (rdbms/get-ds)
                   ["SELECT * FROM settings"])
       first)})

(defn section-data-query [section]
  (-> (sql/select (sql/raw section))
      (sql/from :settings)
      sql/format))

(defn section-data [section]
  (-> (->> (section-data-query section)
           (jdbc/query (rdbms/get-ds))
           first)
      (get section {})))

(defn patch [request]
  (let [{patch :body} request
        {{section :section} :route-params} request
        {{updated_by :id} :authenticated-entity} request
        data (section-data section)
        update (deep-merge data patch)]
    (if (= [1]
           (jdbc/update! (rdbms/get-ds)
                         :settings {section update
                                    :updated_by updated_by} []))
      {:status 204}
      {:status 409})))

(def routes
  (-> (cpj/routes
        (cpj/GET  "/settings/" _ #'get-settings)
        (cpj/PATCH "/settings/:section" _ #'patch))
      wrap-canonicalize-query-params
      (authorize/wrap-require! {:admin true})))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

