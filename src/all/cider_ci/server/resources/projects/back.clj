; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.resources.projects.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.projects :as projects]
    [cider-ci.server.projects.repositories :as repositories]
    [cider-ci.server.resources.api-token.back :as api-token]
    [cider-ci.server.resources.projects.shared :as shared]

    [cider-ci.utils.git-gpg :as git-gpg]
    [cider-ci.utils.honeysql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(def allowed-keys (keys shared/default-project-params))

(defn add-project 
  ([{body :body 
     tx :tx}]
   (add-project (merge shared/default-project-params
                       (select-keys body allowed-keys))
                tx))
  ([data tx]
   (let [project (first (jdbc/insert! tx :projects data))]
     (projects/init-project (assoc project :project-id (:id project)))
     {:body project})))

(defn delete [{body :body tx :tx
                       {project-id :project-id} :route-params}]
  (let [update-params (-> body (select-keys allowed-keys) 
                          (dissoc :id))
        where-clause ["id = ?" project-id]]
    (logging/debug update-params where-clause)
    (if (= 1 (first (jdbc/delete! tx :projects where-clause))) 
      {:status 204}
      (throw (ex-info "Project has not been updated!" {})))))


(defn patch [{body :body tx :tx
              {project-id :project-id} :route-params}]
  (let [update-params (-> body (select-keys allowed-keys) 
                          (dissoc :id))
        where-clause ["id = ?" project-id]]
    (logging/debug update-params where-clause)
    (if (and (not (empty? update-params))
             (= 1 (first (jdbc/update! 
                           tx :projects 
                           update-params where-clause))))
      {:status 204}
      (throw (ex-info "Project has not been updated!" {})))))


(defn project-query [project-id]
  (-> (sql/select :projects.*)
      (sql/from :projects)
      (sql/merge-where [:= :projects.id project-id])
      sql/format))

(defn project [{tx :tx
                {project-id :project-id} :route-params}]
  (when-let [k (->> (project-query project-id)
                    (jdbc/query tx)
                    first)]
    {:body k}))

(defn projects [{tx :tx}]
  {:body {:projects 
          (jdbc/query 
            tx (-> (sql/select :projects.*)
                   (sql/from :projects)
                   sql/format))}})

(def project-path (path :project {:project-id ":project-id"}))

(defn wrap-repository-authorization [handler]
  (fn [request]
    (if (:authenticated-entity request)
      (handler request)
      {:status 401
       :headers {"WWW-Authenticate" "Basic realm=\"Project git access\""}})))


(def routes
  (cpj/routes
    (cpj/GET project-path [] #'project)
    (cpj/DELETE project-path [] #'delete)
    (cpj/GET (path :projects) [] #'projects)
    (cpj/PATCH project-path [] #'patch)
    (cpj/POST (path :projects-add) [] #'add-project)
    (cpj/ANY (path :project-repository 
                   {:project-id ":project-id"
                    :repository-path "*"
                    }) [] (wrap-repository-authorization 
                            #'repositories/http-handler))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
