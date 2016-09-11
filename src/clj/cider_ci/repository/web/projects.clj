(ns cider-ci.repository.web.projects
  (:require
    [cider-ci.repository.web.shared :as web.shared]
    [cider-ci.repository.web.push]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.repositories.fetch-and-update :as fetch-and-update]

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

(defn fetch [request]
  (let [id (-> request :route-params :id)]
    (if-let [repository (-> @state/db :repositories (get (to-cistr id)))]
      (if (= (:state repository) "idle")
        (do (fetch-and-update/fetch-and-update repository)
            {:status 202 :body {:state "fetching" :message "OK"}})
        {:status 409 :body {:message "The repository needs to be in idle state to start fetching."}})
      {:status 404 :body {:message (str "No repository with the id " id " found.")}})))

(defn get-projects [request]
  (let [projects (->> @state/db
                      :repositories
                      (map (fn [[id _]] {:id id})))]
    {:body {:projects projects}}))

(defn get-project [request]
  (let [id (-> request :route-params :id)
        project (-> @state/db :repositories (get id))]
    (if-not project {:status 404}
      {:body (web.shared/filter-repository-params
               project (:authenticated-user request))})))

(defn update-project [request]
  (cond
    (-> request
        :authenticated-user
        :is_admin not) {:status 403
                        :body  "Only admins can update projects!"}
    :else (let [id (-> request :route-params :id)
                params (:body request)
                updated (first (jdbc/update!
                                 (rdbms/get-ds) :repositories
                                 params ["id = ?" id]))]
            (if (= updated 1)
              (do (state/update-repositories)
                  {:status 200
                   :body {:message "updated"}})
              {:status 500
               :body "The project update has not been confirmed"}))))

(def routes
  (cpj/routes
    (cpj/GET "/projects/" _
             (authorize/wrap-require! #'get-projects {:user true}))
    (cpj/POST "/projects/" _
              (authorize/wrap-require! #'create-project {:user true}))
    (cpj/POST "/projects/:id/fetch" _
              (authorize/wrap-require! #'fetch {:user true}))
    (cpj/GET "/projects/:id" _
               (authorize/wrap-require! #'get-project {:user true}))
    (cpj/PATCH "/projects/:id" _
               (authorize/wrap-require! #'update-project {:user true}))
    (cpj/DELETE "/projects/:id" _
                (authorize/wrap-require! #'delete-project {:user true}))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
