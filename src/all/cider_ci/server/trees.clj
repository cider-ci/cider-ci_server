(ns cider-ci.server.trees
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.server.builder :as builder]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [honeysql.core :as sql]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.catcher :as catcher]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn available-jobs [tree-id]
  (try
    (catcher/with-logging {}
      {:status 200
       :headers {"content-type" "application/json;charset=utf-8"}
       :body (builder/available-jobs tree-id)})
    (catch clojure.lang.ExceptionInfo e
      (case (-> e ex-data :object :status)
        404 {:status 404 :body (-> e ex-data str)}
        (throw e)))
    (catch org.yaml.snakeyaml.parser.ParserException e
      {:status 422
       :body "Failed to parse the YAML file."})
    (catch Exception e
      {:status 500
       :body (str "Server error.\n\n"
                  (thrown/stringify e))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn project-configuration [tree-id]
  {:body (builder/project-configuration tree-id)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn jobs [tree-id & [{:keys [recursive] :or {recursive false}}]]
  (let [tree-ids [tree-id]
        jobs (->> (-> (sql/select :*)
                      (sql/from :jobs)
                      (sql/merge-where [:in :tree-id tree-ids])
                      sql/format)
                  (jdbc/query (rdbms/get-ds))
                  (map #([(:id %) %]))
                  (into {}))]
    {:status 200
     :body {:jobs jobs}}))

;(jobs "4bcaa32890f7315fb55462b890d302e8eb01589")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-job [request]
  (let [params {:tree_id (-> request :route-params :tree-id)
                :key (-> request :route-params :job-key)
                :created_by (-> request :authenticated-entity :id)}
        job (builder/create-job params)]
    {:status 201
     :body job}))

(def ^:private routes*
  (cpj/routes
    (cpj/GET "/trees/:tree-id/available-jobs/" [tree-id] (available-jobs tree-id))
    (cpj/GET "/trees/:tree-id/jobs/" [tree-id] (jobs tree-id))
    (cpj/GET "/trees/:tree-id/project-configuration" [tree-id] (project-configuration tree-id))
    (cpj/GET "/trees/:tree-id/project-configuration/dependencies" [tree-id] nil)
    (cpj/POST "/trees/:tree-id/jobs/:job-key" [] #'create-job)
    (cpj/ANY "/trees/*" [] {:status 404 :body "No such route for /trees."})))

(defn routes [request]
  ((authorize/wrap-require! routes* {:user true}) request))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
(debug/debug-ns *ns*)

