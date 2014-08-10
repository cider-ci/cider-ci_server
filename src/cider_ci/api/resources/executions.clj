(ns cider-ci.api.resources.executions
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
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
    [clojure.walk :only [keywordize-keys]]
    [sqlingvo.core]
    ))

(defonce conf (atom nil))


;### get-index ##################################################################

(defn build-executions-base-query []
  (select (distinct [:executions.id :executions.created_at])
          (from :executions)
          (join :commits '(on (= :commits.tree_id :executions.tree_id)) :type :left)
          (join :branches_commits '(on (= :branches_commits.commit_id :commits.id)) :type :left)
          (join :branches '(on (= :branches.id :branches_commits.branch_id)) :type :left)
          (join :repositories '(on (= :branches.repository_id :repositories.id)) :type :left)
          ;(join (as :branches :branch_heads) '(on (= :branch_heads.current_commit_id :commits.id)) :type :left)
          (order-by (desc :executions.created_at))
          (limit 10)))

(defn filter-by-branch-name [query params]
  (if-let [branch-name (:branch-name params)]
    (compose query (where `(= :branches.name ~branch-name) :and))
    query))

(defn filter-by-repository-name [query params]
  (if-let [repository-name (:repository-name params)]
    (compose query (where `(= :repositories.name ~repository-name) :and))
    query))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (compose query (where `(= :executions.state ~state) :and))
    query))

(defn filer-branch-heads [query params]
  (if (contains?  params :branch-heads-only )
    (compose query (where '(= :branches.current_commit_id :commits.id) :and))
    query))


(defn executions-data [params]
  (let [query (-> (build-executions-base-query) 
                  (add-offset params)
                  (filter-by-branch-name params)
                  (filter-by-repository-name params)
                  (filter-by-state params)
                  (filer-branch-heads params)
                  sql)
        _ (logging/debug "GET /executions " {:query query})
        execution-ids (map :id (jdbc/query (:ds @conf) query))]
    {:_links (conj { :self {:href (str (executions-path) "?" 
                                       (build-url-query-string params))}}
                   (curies-link-map)

                   (when (seq execution-ids)
                     {:next {:href (str (executions-path) "?" 
                                        (build-url-query-string 
                                          (next-page-query-params params)))}})

                   (when-let [pp (previous-page-query-params params)]
                     {:previous {:href (str (executions-path) "?" 
                                            (build-url-query-string pp))}})

                   {:cider-ci_api-docs:execution (map execution-link execution-ids)}

                   (root-link-map)

                   )}))

(defn get-index [request] 
  {:hal_json_data (executions-data (:query-params request) )})


;### get-execution-stats ########################################################

(defn get-execution-stats [request]
  (let [id (-> request :params :id)
        data (first (jdbc/query 
                      (:ds @conf) ["SELECT * from execution_stats 
                                   WHERE execution_id = ?::uuid" id]))
        links {:_links 
               (conj {}
                     (curies-link-map)
                     (execution-link-map id)
                     (root-link-map))}]
    {:hal_json_data (conj data links)}))


;### get-execution ##############################################################

(defn query-exeuction [id]
  (first (jdbc/query (:ds @conf) 
                     ["SELECT * from executions
                      WHERE id = ?::UUID" id])))

(defn execution-data [params]
  (let [id (:id params)
        execution (query-exeuction id)]
    (assoc 
      (dissoc execution :substituted_specification_data :tree_id :specification_id)
      :_links (conj 
                { :self (execution-link id)}
                (execution-stats-link-map id)
                (tasks-link-map id)
                (root-link-map)
                (curies-link-map)
                ))))

(defn get-execution [request] 
  {:hal_json_data  (execution-data (:params request))})


;### routes #####################################################################

(defn routes []
  (cpj/routes
    (cpj/GET "/executions" request (get-index request))
    (cpj/GET "/executions/:id" request (get-execution request))
    (cpj/GET "/executions/:id/stats" request (get-execution-stats request))))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


