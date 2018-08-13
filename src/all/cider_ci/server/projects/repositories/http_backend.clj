; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.repositories.http-backend
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.projects.repositories.shared :refer [path]]
    [cider-ci.utils.honeysql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug])
  (:import 
    [java.io File InputStreamReader DataInputStream]
    [java.lang Process ProcessBuilder]
    ))

;;; http git ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-response [process]
  (loop [pout (-> process .getInputStream DataInputStream.)
         response {}
         line (.readLine pout)]
    (if-not (and line (not (re-matches #"^\s*$" line)))
      (assoc response :body pout)
      (let [[k v] (clojure.string/split line #":\s+" 2)]
        (recur pout 
               (if (re-matches #"(?i)^status" k)
                 (let [status (-> v (clojure.string/split #"\s+" 2) first Integer/parseInt)]
                   (assoc response :status status))
                 (assoc-in response [:headers k] v))
               (.readLine pout))))))

(defn project [project-id tx]
  (->> (-> (sql/select :*)
           (sql/from :projects)
           (sql/merge-where [:= :id project-id])
           sql/format)
       (jdbc/query tx)
       first))

(defn http-handler [{request-method :request-method
                     remote-addr :remote-addr
                     {project-id :project-id
                      repository-path :repository-path} :route-params
                     query-string :query-string
                     tx :tx
                     :as request}]
  (let [env {"GIT_PROJECT_ROOT" (.toString (path {:project-id project-id}))
             "PATH_INFO" repository-path
             "GIT_HTTP_EXPORT_ALL" "true"
             "REMOTE_USER" (or (-> request :authenticated-entity :primary_email_address)
                               "unknown user")
             "REMOTE_ADDR" (or remote-addr "localhost")
             "CONTENT_TYPE" (get-in request [:headers "content-type"]) 
             "QUERY_STRING" query-string
             "REQUEST_METHOD" (clojure.string/upper-case (str request-method))}
        process-builder (ProcessBuilder. (into-array String ["git" "http-backend"]))
        _ (.redirectErrorStream process-builder true)
        process-environment (.environment process-builder)
        _ (.clear process-environment)
        _ (doseq [[k v] env] (when v (.put process-environment k v)))
        process (.start process-builder)
        _ (when-let [is (:body request)]
            (let [os (.getOutputStream process)]
              (future (try (.transferTo is os)
                           (finally (.close is) (.close os))))))
        project (project project-id tx)
        response (if project 
                   (build-response process)
                   {:status 404
                    :body "no such project"})]
    (when (#{:post :delete :put :patch} request-method)
      (->> (-> (sql/update :projects)
               (sql/sset {:repository_updated_at (sql/raw "now()")})
               (sql/merge-where [:= :id project-id])
               sql/format)
           (jdbc/execute! tx)))
    (logging/debug process-environment)
    (logging/debug response)
    response))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
(debug/debug-ns *ns*)
