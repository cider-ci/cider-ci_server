(ns cider-ci.rm.web
  (:require 
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.rm.submodules :as submodules]
    [cider-ci.utils.exception :as utils.execption]
    [cider-ci.utils.http-server :as http-server]
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


;##### authorization ########################################################## 
(defn return-authenticate! [request]
  {:status 401
   :headers 
   {"WWW-Authenticate" 
    "Basic realm=\"Cider-CI; sign in or provide credentials\""}
   })

(defn authorize [request handler] 
  (cond
    (:authenticated-application request) (handler request) 
    :else (return-authenticate! request)))

(defn wrap-authorize [handler]
  (fn [request]
    (authorize request handler)))


;##### authorization ########################################################## 
(defn build-routes [context]
  (cpj/routes 
    (cpj/context (str context "/repositories") []
                 (cpj/GET "/submodules/:commit-id" [commit-id] 
                          (get-submodules commit-id))
                 (cpj/GET "/:id/git/*" request
                          (get-git-file request)) 
                 )))

(defn wrap-debug-logging [handler]
  (fn [request]
    (let [wrap-debug-logging-level (or (:wrap-debug-logging-level request) 0 )]
      (logging/debug "wrap-debug-logging " wrap-debug-logging-level " request: " request)
      (let [response (handler (assoc request :wrap-debug-logging-level (+ wrap-debug-logging-level 1)))]
        (logging/debug  "wrap-debug-logging " wrap-debug-logging-level " response: " response)
        response))))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (wrap-debug-logging)
       (ring.middleware.json/wrap-json-params)
       (wrap-debug-logging)
       (wrap-authorize)
       (wrap-debug-logging)
       (http-basic/wrap)
       (wrap-debug-logging)))


;#### the server ##############################################################
(defn initialize [new-conf]
  (reset! conf new-conf)
  (http-server/start @conf (build-main-handler)))


