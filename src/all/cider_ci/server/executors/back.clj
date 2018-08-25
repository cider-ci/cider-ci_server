; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.executors.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.executors.shared :as executors-shared]
    ;[cider-ci.server.executors :as executors]

    [cider-ci.utils.honeysql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))

(defn upsert [data]
    (-> (sql/insert-into :executors)
        (sql/values [(select-keys data executors-shared/admin-action-accepted-keys)])
        (sql/upsert (-> ;(apply sql/do-update-set executors-shared/admin-action-accepted-keys)
                        (sql/on-conflict :id)
                        ((fn [sql]
                           (apply sql/do-update-set sql executors-shared/admin-action-accepted-keys)
                           ))))
        (sql/returning :*)
        sql/format))

(defn put [{tx :tx body :body}]
  {:body (->> (upsert body)
              ((fn [sql] (jdbc/execute! tx sql {:return-keys true}))))})

(defn insert [data]
  (-> (sql/insert-into :executors)
      (sql/values [(select-keys data executors-shared/admin-action-accepted-keys)])
      (sql/returning :*)
      sql/format))

(defn post [{tx :tx body :body}]
  {:body 
   (->> (insert body)
        ((fn [sql] (jdbc/execute! tx sql {:return-keys true}))))})


(defn excutor [{tx :tx {id :executor-id} :route-params}]
  (if-let [excutor (->> ["SELECT * FROM executors where id = ?" id]
       (jdbc/query tx)
       first)]
    {:body excutor}
    {:status 404}))

(def executor-path (path :executor {:executor-id ":executor-id"}))

(def routes
  (cpj/routes
    (cpj/GET executor-path [] #'excutor)
    (cpj/PUT executor-path [] #'put)
    (cpj/POST executor-path [] #'post)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
(debug/debug-ns *ns*)
