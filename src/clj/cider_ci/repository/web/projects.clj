(ns cider-ci.repository.web.projects
  (:require
    [cider-ci.repository.web.push]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.repositories.fetch-and-update :as fetch-and-update]

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
                                 (:json-params request)))]
            (cider-ci.repository.web.push/push-to-all-clients)
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
            (cider-ci.repository.web.push/push-to-all-clients)
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

(defn update-project [request]
  (cond
    (-> request
        :authenticated-user
        :is_admin not) {:status 403
                        :body  "Only admins can update projects!"}
    :else (let [updated (first (jdbc/update!
                                 (rdbms/get-ds) :repositories
                                 (:json-params request)
                                 ["id = ?" (-> request :route-params :id)]
                                 ))]
            (if (= updated 1)
              (do (cider-ci.repository.web.push/push-to-all-clients)
                  {:status 200
                   :body {:message "updated"}})
              {:status 500
               :body "The project updated has not been confirmed"}))))




;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
