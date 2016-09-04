; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web
  (:require
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.repository :as sql.repository]
    [cider-ci.repository.web.ls-tree :as web.ls-tree]
    [cider-ci.repository.web.update-notifications :as update-notifications]
    [cider-ci.repository.web.shared :refer :all]
    [cider-ci.repository.web.ui :as web.ui]
    [cider-ci.repository.web.push :as web.push]
    [cider-ci.repository.web.project-configuration :as web.project-configuration]
    [cider-ci.repository.web.projects :as web.projects]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]

    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.accept]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.defaults]
    [ring.middleware.json]
    [ring.middleware.params]
    [ring.util.response :refer [charset]]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

;##### get file ###############################################################

; TODO: check if this is still used
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


;##### routes #################################################################

(def routes
  (cpj/routes
    (cpj/GET "/project-configuration/:id" _
             (authorize/wrap-require!
               #'web.project-configuration/project-configuration
               {:service true}))
    (cpj/GET "/ls-tree" _
             (authorize/wrap-require! #'web.ls-tree/ls-tree {:service true}))
    (cpj/GET "/path-content/:id/*" _
             (authorize/wrap-require! #'get-path-content {:service true}))
    (cpj/GET "/:id/git/*" _
             (authorize/wrap-require! #'get-git-file {:service true}))
    (cpj/POST "/projects/" _
              (authorize/wrap-require! #'web.projects/create-project {:user true}))
    (cpj/POST "/projects/:id/fetch" _
              (authorize/wrap-require! #'web.projects/fetch {:user true}))
    (cpj/PATCH "/projects/:id" _
                (authorize/wrap-require! #'web.projects/update-project {:user true}))
    (cpj/DELETE "/projects/:id" _
              (authorize/wrap-require! #'web.projects/delete-project {:user true}))
    ))

(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept
    handler
    {:mime
     [; "application/json-roa+json" :qs 1
      "application/json" :qs 1.0 :as :json
      "text/html" :qs 0.99 :as :html
      ]}))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      (cpj.handler/api routes)
      ring.middleware.json/wrap-json-response
      routing/wrap-shutdown
      (ring.middleware.params/wrap-params)
      ring.middleware.json/wrap-json-params
      status/wrap
      web.ui/wrap
      wrap-accept
      web.push/wrap
      (http-basic/wrap {:service true :user true})
      anti-forgery/wrap
      session/wrap
      cookies/wrap-cookies
      identity-with-logging
      (ring.middleware.defaults/wrap-defaults {:static {:resources "public"}})
      update-notifications/wrap
      (routing/wrap-prefix context)
      routing/wrap-exception))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
