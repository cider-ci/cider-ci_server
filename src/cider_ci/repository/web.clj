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
    [drtom.logbug.debug :as debug]
    [drtom.logbug.ring :refer [wrap-handler-with-logging]]
    [drtom.logbug.thrown :as thrown]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    [ring.middleware.params]
    [ring.util.response]
    ))


(defonce conf (atom {}))

(defn get-git-file [request]
  (logging/debug get-git-file [request])
  (let [repository-id (:id (:route-params request))
        relative-web-path (:* (:route-params request))
        relative-file-path (str (-> @conf :services :repository :repositories :path) "/" repository-id "/" relative-web-path)
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
  (logging/info request)
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


(defn ls-tree [request]
  (logging/info 'ls-tree request)
  (let [id (-> request :params :id)
        include-regex (-> request :params :include-match)
        exclude-regex (-> request :params :exclude-match)]
    (when-let [repository (sql.repository/resolve id)]
      {:headers {"Content-Type" "application/json"}
       :body (json/write-str (git.repositories/ls-tree repository id include-regex exclude-regex))}
      )))

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
    (cpj/GET "/path-content/:id/*" request
             (get-path-content request))
    (cpj/GET "/ls-tree/:id/" _ ls-tree)
    (cpj/GET "/:id/git/*" request
             (get-git-file request))

    ))


(defn build-main-handler [context]
  ( -> (cpj.handler/api (build-routes context))
       (wrap-handler-with-logging 'cider-ci.dispatcher.web)
       routing/wrap-shutdown
       (wrap-handler-with-logging 'cider-ci.dispatcher.web)
       (ring.middleware.params/wrap-params)
       (wrap-handler-with-logging 'cider-ci.repository.web)
       (ring.middleware.json/wrap-json-params)
       (wrap-handler-with-logging 'cider-ci.repository.web)
       (wrap-status-dispatch)
       (wrap-handler-with-logging 'cider-ci.repository.web)
       (routing/wrap-prefix context)
       (wrap-handler-with-logging 'cider-ci.repository.web)
       (auth/wrap-authenticate-and-authorize-service)
       (wrap-handler-with-logging 'cider-ci.repository.web)
       (http-basic/wrap {:executor true :user false :service true})
       (wrap-handler-with-logging 'cider-ci.repository.web)
       (routing/wrap-log-exception)
       ))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (let [http-conf (-> @conf :services :repository :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
