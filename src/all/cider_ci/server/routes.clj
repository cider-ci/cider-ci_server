; Copyright © 2018 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.routes
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.constants :as constants]
    [cider-ci.env :as env]
    [cider-ci.server.anti-csrf.core :as anti-csrf]
    [cider-ci.server.html :as html]
    [cider-ci.server.jobs.back :as jobs]
    [cider-ci.server.paths :refer [path paths]]
    [cider-ci.server.resources.api-token.back :as api-token]
    [cider-ci.server.resources.api-tokens.back :as api-tokens]
    [cider-ci.server.resources.auth.back :as auth]
    [cider-ci.server.resources.commits.back :as commits]
    [cider-ci.server.resources.email-addresses.back :as email-addresses]
    [cider-ci.server.resources.gpg-keys.back :as gpg-keys]
    [cider-ci.server.resources.initial-admin.back :as initial-admin]
    [cider-ci.server.resources.projects.back :as projects]
    [cider-ci.server.resources.settings.back :as settings]
    [cider-ci.server.resources.user.back :as user]
    [cider-ci.server.resources.users.back :as users]
    [cider-ci.server.socket :as socket]
    [cider-ci.server.status.back :as status]
    [cider-ci.server.trees.back :as trees]
    [cider-ci.utils.http-resources-cache-buster :as cache-buster :refer [wrap-resource]]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as ds]
    [cider-ci.utils.ring-exception :as ring-exception]

    [bidi.bidi :as bidi]
    [bidi.ring :refer [make-handler]]
    [cheshire.core :as json]
    [compojure.core :as cpj]
    [ring.middleware.accept]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.cookies]
    [ring.middleware.json]
    [ring.middleware.params]
    [ring.util.response :refer [redirect]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(declare redirect-to-root-handler)

(def skip-authorization-handler-keys
  #{
    :auth-password-sign-in
    :auth-sign-in
    :initial-admin
    :project-repository
    :status
    })

(def do-not-dispatch-to-std-frontend-handler-keys
  #{:redirect-to-root 
    :not-found 
    :project-repository
    :websockets
    })

(def handler-resolve-table
  {:api-token api-token/routes
   :api-tokens api-tokens/routes
   :auth auth/routes
   :auth-info auth/routes
   :auth-sign-in auth/routes
   :auth-password-sign-in auth/routes
   :auth-sign-out auth/routes
   :commits commits/routes
   :email-address email-addresses/routes
   :email-addresses email-addresses/routes
   :email-addresses-add email-addresses/routes
   :project projects/routes
   :project-repository projects/routes
   :projects projects/routes
   :gpg-key gpg-keys/routes
   :gpg-keys gpg-keys/routes
   :gpg-keys-add gpg-keys/routes
   :initial-admin initial-admin/routes
   :jobs jobs/routes
   :projects-add projects/routes
   :tree-jobs trees/routes
   :tree-project-configuration trees/routes
   :user user/routes
   :users users/routes
   :websockets socket/routes
   })


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn redirect-to-root-handler [request]
  (redirect (path :root)))

(defn handler-resolver [handler-key]
  (get handler-resolve-table handler-key nil))

(defn dispatch-to-handler [request]
  (if-let [handler (:handler request)]
    (handler request)
    (throw 
      (ex-info 
        "There is no handler for this resource and the accepted content type."
        {:status 404}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn browser-request-matches-javascript? [request]
  "Returns true if the accepted type is javascript or
  if the :uri ends with .js. Note that browsers do not 
  use the proper accept type for javascript script tags."
  (boolean (or (= (-> request :accept :mime) :javascript)
               (re-find #".+\.js$" (or (-> request :uri presence) "")))))

(defn wrap-dispatch-content-type
  ([handler]
   (fn [request]
     (wrap-dispatch-content-type handler request)))
  ([handler request]
   (cond
     ; accept json always goes to the backend handlers, i.e. the normal routing
     (= (-> request :accept :mime) :json) (or (handler request)
                                              (throw (ex-info "This resource does not provide a json response."
                                                              {:status 406})))
     ; accept HTML and GET (or HEAD) wants allmost always the frontend
     (and (= (-> request :accept :mime) :html)
          (#{:get :head} (:request-method request))
          (not (do-not-dispatch-to-std-frontend-handler-keys 
                 (:handler-key request)))
          (not (browser-request-matches-javascript? request))
          ) (html/html-handler request)
     ; other request might need to go the backend and return frontend nevertheless
     :else (let [response (handler request)]
             (if (and (nil? response)
                      ; TODO we might not need the following after we check (?nil response)
                      (not (do-not-dispatch-to-std-frontend-handler-keys
                             (:handler-key request)))
                      (not (#{:post :put :patch :delete} (:request-method request)))
                      (= (-> request :accept :mime) :html)
                      (not (browser-request-matches-javascript? request)))
               (html/html-handler request)
               response)))))

(defn wrap-resolve-handler
  ([handler]
   (fn [request]
     (wrap-resolve-handler handler request)))
  ([handler request]
   (let [path (or (-> request :path-info presence)
                  (-> request :uri presence))
         {route-params :route-params
          handler-key :handler} (bidi/match-pair paths {:remainder path
                                                        :route paths})
         handler-fn (handler-resolver handler-key)]
     (handler (assoc request
                     :route-params route-params
                     :handler-key handler-key
                     :handler handler-fn)))))

;(bidi/match-pair paths {:remainder "/projects/x1" :route paths})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept
    handler
    {:mime
     ["application/json" :qs 1 :as :json
      "application/json-roa+json" :qs 1 :as :json-roa
      "image/apng" :qs 0.8 :as :apng
      "text/css" :qs 1 :as :css 
      "text/html" :qs 1 :as :html]}))

(defn canonicalize-params-map [params]
  (if-not (map? params)
    params
    (->> params
         (map (fn [[k v]]
                [(keyword k)
                 (try (json/parse-string v true)
                      (catch Exception _ v))]))
         (into {}))))

(defn wrap-canonicalize-params-maps [handler]
  (fn [request]
    (handler (-> request
                 (update-in [:params] canonicalize-params-map)
                 (update-in [:query-params] canonicalize-params-map)
                 (update-in [:form-params] canonicalize-params-map)))))

(defn wrap-empty [handler]
  (fn [request]
    (or (handler request)
        {:status 404})))

(defn wrap-secret-byte-array
  "Adds the secret into the request as a byte-array (to prevent
  visibility in logs etc) under the :secret-byte-array key."
  ([handler secret]
   (assert (presence secret))
   (fn [request]
     (wrap-secret-byte-array handler secret request)))
  ([handler secret request]
   (handler (assoc request :secret-ba (.getBytes secret)))))

(defn init [secret]
  (I> wrap-handler-with-logging
      dispatch-to-handler
      (auth/wrap-authorize skip-authorization-handler-keys)
      wrap-dispatch-content-type
      anti-csrf/wrap
      auth/wrap-authenticate
      ring.middleware.cookies/wrap-cookies
      wrap-empty
      (wrap-secret-byte-array secret)
      initial-admin/wrap
      settings/wrap
      ds/wrap-tx
      status/wrap
      ring.middleware.json/wrap-json-response
      (ring.middleware.json/wrap-json-body {:keywords? true})
      wrap-accept
      wrap-resolve-handler
      wrap-canonicalize-params-maps
      ring.middleware.params/wrap-params
      (wrap-resource
        "public" {:allow-symlinks? true
                  :cache-bust-paths ["css/site.css"
                                     "css/site.min.css"
                                     "js/app.js"]
                  :never-expire-paths [#".*font-awesome-[^\/]*\d\.\d\.\d\/.*"
                                       #".+_[0-9a-f]{40}\..+"]
                  :enabled? (= env/env :prod)})
      wrap-content-type
      ring-exception/wrap))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
