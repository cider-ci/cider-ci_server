(ns cider-ci.rm.web
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [cider-ci.rm.submodules :as submodules]
    [cider-ci.utils.exception :as utils.execption]
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

(defn build-routes [context]
  (cpj/routes 
    (cpj/context (str context "/repositories") []
                 (cpj/GET "/submodules/:commit-id" [commit-id] 
                          (get-submodules commit-id))
                 (cpj/GET "/:id/git/*" request
                          (get-git-file request)) 
                 )))

                    
(defn log-handler [handler]
  (fn [request]
    (logging/debug [log-handler request])
    (handler request)))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (log-handler)
       (ring.middleware.json/wrap-json-params)))

(defonce server nil)

(defn stop-server []
  (logging/info "stopping server")
  (. server stop)
  (def server nil))

(defn start-server []
  "Starts (or stops and then starts) the webserver"
  (let [server-conf (conj {:ssl? false
                           :join? false} 
                          (select-keys (:web @conf) [:port :host]))]
    (if server (stop-server)) 
    (logging/info "starting server " server-conf)
    (def server (jetty/run-jetty (build-main-handler) server-conf))))

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-server))
