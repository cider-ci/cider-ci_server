(ns cider-ci.server.trees.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require

    [cider-ci.server.paths :refer [path]]

    [cider-ci.server.builder :as builder]
    ; TODO
    ;[cider-ci.auth.authorize :as authorize]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [cider-ci.utils.honeysql :as sql]


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

(defn project-configuration [{{tree-id :tree-id} :route-params}]
  {:body (builder/project-configuration tree-id)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn jobs [{{tree-id :tree-id} :route-params tx :tx} 
            & [{:keys [recursive] :or {recursive false}}]]
  (let [tree-ids [tree-id]
        jobs (->> (-> (sql/select :*)
                      (sql/from :jobs)
                      (sql/merge-where [:in :tree-id tree-ids])
                      sql/format)
                  (jdbc/query tx)
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
    ;(cpj/GET "/trees/:tree-id/available-jobs/" [tree-id] (available-jobs tree-id))
    (cpj/GET (path :tree-jobs {:tree-id ":tree-id"} {}) [] {:status 418})
    (cpj/GET (path :tree-project-configuration {:tree-id ":tree-id"}) [] project-configuration)
    ;(cpj/GET "/trees/:tree-id/project-configuration/dependencies" [tree-id] nil)
    ;(cpj/POST "/trees/:tree-id/jobs/:job-key" [] #'create-job)
    (cpj/ANY "/trees/*" [] {:status 404 :body "No such route for /trees."})
    ))

; TODO authorization
(def routes routes*)



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
