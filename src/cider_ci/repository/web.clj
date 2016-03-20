; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web
  (:require
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.project-configuration :as project-configuration]
    [cider-ci.repository.repositories :as repositories]
    [cider-ci.repository.sql.repository :as sql.repository]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]

    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    [ring.middleware.params]
    [ring.util.response :refer [charset]]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

;##### get file ###############################################################

(defn get-git-file [request]
  (logging/debug get-git-file [request])
  (let [repository-id (:id (:route-params request))
        relative-web-path (:* (:route-params request))
        relative-file-path (str (-> (get-config) :services :repository :repositories :path) "/" repository-id "/" relative-web-path)
        file (clojure.java.io/file relative-file-path)
        abs-path (.getAbsolutePath file)]
    (logging/debug {:repositories-id repository-id
                    :relative-file-path relative-file-path
                    :abs-path abs-path
                    :file-exists? (.exists file)})
    (if (.exists file)
      (ring.util.response/file-response relative-file-path nil)
      {:status 404})))

(defn respond-with-500 [request ex]
  (logging/warn "RESPONDING WITH 500" {:exception (thrown/stringify ex) :request request})
  {:status 500 :body (thrown/stringify ex)})

(defn get-path-content [request]
  (logging/debug request)
  (try
    (let [id (-> request :route-params :id)
          path (-> request :route-params :*)]
      (when-let [repository (sql.repository/resolve id)]
        (when-let [content  (git.repositories/get-path-contents repository id path)]
          {:body content})))
    (catch clojure.lang.ExceptionInfo e
      (cond (re-find #"does not exist in"  (str e)) {:status 404 :body (-> e ex-data :err)}
            :else (respond-with-500 request e)))
    (catch Exception e
      (respond-with-500 request e))))


;#### repository update notification ##########################################

(defn update-notification-handler [request]
  (if-let [repository (sql.repository/get-repository-by-update-notification-token
                        (-> request :params :update_notification_token))]
    (do (repositories/update-repository repository)
      {:status 202 :body "OK"})
    {:status 404 :body "The corresponding repository was not found"}))

(defn wrap-repositories-update-notifications [default-handler]
  (cpj/routes
    (cpj/POST "/update-notification/:update_notification_token"
              _ #'update-notification-handler)
    (cpj/ANY "*" _ default-handler)))


;##### project configuration ##################################################

(defn get-project-configuration [request]
  (logging/info request)
  (-> (try
        (when-let [content (project-configuration/build-project-configuration
                             (-> request :params :id))]
          {:body (json/write-str content :key-fn #(subs (str %) 1))
           :headers {"Content-Type" "application/json"}})
        (catch clojure.lang.ExceptionInfo e
          (case (-> e ex-data :status )
            404 {:status 404
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str (ex-data e)) }
            422 {:status 422
                 :body (thrown/stringify e)}
            (respond-with-500 request e)))
        (catch Throwable e
          (respond-with-500 request e)))
      (charset "UTF-8")))


;##### routes #################################################################

(def routes
  (cpj/routes
    (cpj/GET "/project-configuration/:id" _ get-project-configuration)
    (cpj/GET "/path-content/:id/*" _ get-path-content)
    (cpj/GET "/:id/git/*" _ get-git-file )))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      (cpj.handler/api routes)
      routing/wrap-shutdown
      (ring.middleware.params/wrap-params)
      (ring.middleware.json/wrap-json-params)
      status/wrap
      (authorize/wrap-require! {:service true})
      (http-basic/wrap {:service true})
      wrap-repositories-update-notifications
      (routing/wrap-prefix context)
      (routing/wrap-log-exception)))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
