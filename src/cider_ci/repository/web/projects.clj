; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.projects
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.status-pushes.core :as status-pushes]
    [cider-ci.repository.branch-updates.core :as branch-updates]
    [cider-ci.repository.fetch-and-update.core :as fetch-and-update]
    [cider-ci.repository.push-hooks.core :as push-hooks]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.web.push]
    [cider-ci.repository.web.shared :as web.shared]
    [cider-ci.repository.web.projects.update :as web.projects.update]
    [cider-ci.repository.remote :as remote]

    [cider-ci.auth.authorize :as authorize]

    [honeysql.core :as sql]
    [compojure.core :as cpj]
    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.core :refer :all]

    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))


(defn create-project [request]
  (cond
    (-> request
        :authenticated-user
        :is_admin not) {:status 403
                        :body  "Only admins can create new projects!"}
    :else (let [created (first (jdbc/insert!
                                 (rdbms/get-ds) :repositories
                                 (:body request)))]
            (state/update-repositories)
            {:status 201
             :body created})))

(defn delete-project [request]
  (cond
    (-> request
        :authenticated-user
        :is_admin not) {:status 403
                        :body  "Only admins can delete new projects!"}
    :else (let [deleted (jdbc/delete!
                          (rdbms/get-ds) :repositories
                          ["id = ?" (-> request :route-params :id)])]
            (state/update-repositories)
            {:status 204
             :body deleted})))

(defn get-repository [id]
  (-> (state/get-db) :repositories (get (keyword id))))

(defn fetch [request]
  (let [id (-> request :route-params :id)]
    (if-let [repository (get-repository id) ]
      (do (fetch-and-update/fetch-and-update repository)
          {:status 202 :body {:fetch-and-update-state "fetching" :message "OK"}})
      {:status 404 :body {:message (str "No repository with the id " id " found.")}})))

(defn update-branches [request]
  (let [id (-> request :route-params :id)]
    (if-let [repository (get-repository id) ]
      (do (branch-updates/update repository)
          {:status 202 :body {:message "OK"}})
      {:status 404 :body {:message (str "No repository with the id " id " found.")}})))

(defn set-up-and-check-push-hook [request]
  (let [id (-> request :route-params :id)]
    (if-let [repository (get-repository id)]
      (if-let [hook (push-hooks/set-up-and-check repository)]
        {:status 202 :body hook}
        {:status 412 :body {:message "Push hooks can not be set-up for this repository!"}})
      {:status 404 :body {:message (str "No repository with the id " id " found.")}})))

(defn get-projects [request]
  (let [projects (->> (state/get-db)
                      :repositories
                      (map (fn [[id _]] {:id (str id)})))]
    {:body {:projects projects}}))

(defn get-project [request]
  (let [id (-> request :route-params :id)
        project (get-repository id)]
    (if-not project {:status 404}
      {:body (web.shared/filter-repository-params
               project (:authenticated-user request))})))

(defn push-statuses [request]
  (let [id (-> request :route-params :id)]
    (let [repository (get-repository id)]
      (cond (nil?  repository) {:status 404 :body {}}
            (not (remote/api-access? repository)) {:status 412 :body {}}
            :else (do (status-pushes/push-recent-statuses-for-repository id)
                      {:status 202 :body {}})))))

(def routes
  (cpj/routes
    (cpj/GET "/projects/" _
             (authorize/wrap-require! #'get-projects {:user true}))
    (cpj/POST "/projects/" _
              (authorize/wrap-require! #'create-project {:user true}))
    (cpj/POST "/projects/:id/fetch" _
              (authorize/wrap-require! #'fetch {:user true}))
    (cpj/POST "/projects/:id/update-branches" _
              (authorize/wrap-require! #'update-branches {:user true}))
    (cpj/POST "/projects/:id/push-hook" _
              (authorize/wrap-require! #'set-up-and-check-push-hook {:user true}))
    (cpj/POST "/projects/:id/push-statuses" _
              (authorize/wrap-require! #'push-statuses {:user true}))
    (cpj/GET "/projects/:id" _
               (authorize/wrap-require! #'get-project {:user true}))
    (cpj/PATCH "/projects/:id" _
               (authorize/wrap-require! #'web.projects.update/update-project {:user true}))
    (cpj/DELETE "/projects/:id" _
                (authorize/wrap-require! #'delete-project {:user true}))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)
(debug/debug-ns 'cider-ci.auth.authorize)
