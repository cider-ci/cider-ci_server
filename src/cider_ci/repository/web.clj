; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.core]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.repository :as sql.repository]
    [cider-ci.repository.submodules :as submodules]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as utils.execption]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    [ring.util.response]
    ))


(defonce conf (atom {}))

(defn get-submodules [commit-id]
  (try 
    (let [sms (submodules/submodules-for-commit commit-id)]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/write-str sms)})
    (catch Exception e
      (logging/warn (utils.execption/stringify e))
      {:status 404}
      )))

(defn get-git-file [request]
  (logging/debug get-git-file [request])
  (let [repository-id (:id (:route-params request))
        relative-web-path (:* (:route-params request))
        relative-file-path (str (:path (:repositories @conf)) "/" repository-id "/" relative-web-path) 
        file (clojure.java.io/file relative-file-path)
        abs-path (.getAbsolutePath file)]
    (logging/debug [repository-id relative-web-path relative-file-path (.exists file)])
    (if (.exists file)
      (ring.util.response/file-response relative-file-path nil)
      {:status 404})))

(defn get-path-content [request]
  (logging/info request)
  (let [id (-> request :route-params :id)
        path (-> request :route-params :*)]
    (when-let [repository (sql.repository/resolve id)]
      (when-let [content  (git.repositories/get-path-contents repository id path)]
        {:body content}))))


;##### status dispatch ######################################################## 

(defn status-handler [request]
  (let [stati {:rdbms (rdbms/check-connection)
               :messaging (messaging/check-connection)
               }]
    (if (every? identity (vals stati))
      {:status 200
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} }
      {:status 511
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} })))

(defn wrap-status-dispatch [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))


;##### routes ################################################################# 

(defn build-routes [context]
  (cpj/routes 
    (cpj/GET "/submodules/:commit-id" [commit-id] 
             (get-submodules commit-id))
    (cpj/GET "/path-content/:id/*" request 
             (get-path-content request))
    (cpj/GET "/:id/git/*" request
             (get-git-file request)) 
    ))

(defn build-main-handler [context]
  ( -> (cpj.handler/api (build-routes context))
       (routing/wrap-debug-logging 'cider-ci.repository.web)
       (ring.middleware.json/wrap-json-params)
       (routing/wrap-debug-logging 'cider-ci.repository.web)
       (wrap-status-dispatch)
       (routing/wrap-debug-logging 'cider-ci.repository.web)
       (routing/wrap-prefix context)
       (routing/wrap-debug-logging 'cider-ci.repository.web)
       (auth/wrap-authenticate-and-authorize-service)
       (routing/wrap-debug-logging 'cider-ci.repository.web)
       (http-basic/wrap)
       (routing/wrap-debug-logging 'cider-ci.repository.web)
       (routing/wrap-log-exception)
       ))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (let [http-conf (-> @conf :http_server)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))



;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
