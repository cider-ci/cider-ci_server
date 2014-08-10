(ns cider-ci.api.resources.tasks
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms.json]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    [sqlingvo.core :as sqling]
    ) 
  (:refer-clojure :exclude [distinct group-by])
  (:use 
    [cider-ci.api.resources.shared :exclude [initialize]]
    [sqlingvo.core]
    ))

(defonce conf (atom nil))

;### get tasks ##################################################################

(defn build-tasks-base-query [execution-id]
  (select (distinct [:tasks.id :tasks.name :tasks.updated_at])
          (from :tasks)
          (where `(= :execution_id ~(uuid execution-id)) :and)
          (order-by (asc :tasks.name))
          (limit 10)))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (compose query (where `(= :tasks.state ~state) :and))
    query))


(defn tasks-data [execution-id query-params]
  (let [query (-> (build-tasks-base-query execution-id)
                  (filter-by-state query-params)
                  (add-offset query-params)
                  sql)
        task-ids (map :id (jdbc/query (:ds @conf) query))]
    {:_links (conj {:self {:href (str (tasks-path execution-id) "?"
                                      (build-url-query-string query-params))}
                    :cider-ci_api-docs:task (map task-link task-ids)}

                   (when-let [pp (previous-page-query-params query-params)]
                     {:previous {:href (str (tasks-path execution-id) "?" 
                                            (build-url-query-string pp))}})

                    (when (seq task-ids)
                      {:next {:href (str (tasks-path execution-id) "?" 
                                         (build-url-query-string
                                           (next-page-query-params query-params)))}})

                   (execution-link-map execution-id)
                    
                   (curies-link-map)
                   )}))
  
(defn get-index [request] 
  {:hal_json_data (tasks-data (-> request :params :execution_id)
                              (-> request :query-params))})

;### get task ###################################################################

(defn get-task [request]
  (logging/debug request)
  (let [uuid (-> request :params :id uuid)
        task (first (jdbc/query (:ds @conf) ["SELECT * from tasks WHERE id = ?" uuid]))]
    (logging/debug uuid)
    {:hal_json_data task}
    ))


;### routes #####################################################################

(defn routes []
  (cpj/routes
    (cpj/GET "/executions/:execution_id/tasks" request (get-index request))
    (cpj/GET "/tasks/:id" request (get-task request))
    ;(cpj/GET "/tasks/:id/stats" request (get-task-stats request))
    ))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))



;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)



