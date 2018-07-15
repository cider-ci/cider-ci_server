; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.client.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])

  (:require
    [cider-ci.server.client.ui.navbar.release :as navbar.release]
    [cider-ci.server.client.constants :refer [CONTEXT]]

    [cider-ci.server.client.web.shared :as web.shared :refer [dynamic]]
    [cider-ci.server.client.welcome-page.be :as welcome-page]
    [cider-ci.server.client.root :as root]
    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http-resources-cache-buster :as cache-buster]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]
    [cider-ci.utils.markdown :refer [md2html]]

    [cider-ci.env]

    [clj-time.core :as time]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.walk :refer [keywordize-keys]]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [hiccup.page :refer [include-js include-css html5]]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.accept]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.defaults]
    [ring.middleware.json]
    [ring.middleware.params]
    [ring.util.response :refer [charset]]
    [ring.util.response]
    [yaml.core :as yaml]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(def routes
  (cpj/routes
    (cpj/GET "/" [] #'dynamic)
    (cpj/GET "/initial-admin" [] #'dynamic)
    (cpj/GET "/debug" [] #'dynamic)
    (cpj/GET "/*" [] #'dynamic)
    ))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      routes
      welcome-page/wrap
      ; authentication and primitive authorization
      cider-ci.utils.ring/wrap-keywordize-request
      cookies/wrap-cookies
      ring.middleware.params/wrap-params
      (ring.middleware.defaults/wrap-defaults {:static {:resources "public"}})
      status/wrap
      (routing/wrap-prefix context)
      routing/wrap-exception))

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (str CONTEXT
                     (cache-buster/cache-busted-path
                       (if (= cider-ci.env/env :dev)
                         "/css/site.css"
                         "/css/site.min.css"))))])

(def default-welcome-message
"
# Welcome to Cider-CI

This is the default welcome-message.

It can be customized via `More` → `Settings` → `Welcome-Page`!
")

(def about-pre-message
  (->> [" # About Cider-CI                                                    "
        " Cider-CI is an application and service stack                        "
        " for highly **parallelized and resilient testing**, and              "
        " **continuous delivery**.                                            "
        "                                                                     "
        " Read more about Cider-CI at [cider-ci.info](http://cider-ci.info/). "]
       (clojure.string/join  \newline)
       md2html))

(def release-info
  (try 
    (-> "releases.yml"
        clojure.java.io/resource
        slurp yaml/parse-string
        :releases first)
    (catch Exception _
      {:version_major 5
       :version_minor 0
       :version_patch 0
       :version_pre "development"
       :name "Ortles"
       :version_build nil 
       :edition nil
       :about-name ""})))

(def release-notes
  [:div.release-notes
   [:div.about-name (-> release-info :about-name md2html)]
   [:div.description (-> release-info :description md2html)]])

(defn welcome-message []
  (md2html
    (or (-> (get-config) :welcome_page :message presence)
        default-welcome-message)))

(defn mount-target []
  [:div#app
   [:div
    (if (= cider-ci.env/env :dev)
      [:div.alert.alert-warning
       [:h3 "ClojureScript has not been compiled!"]
       [:p "This page depends on JavaScript!"]
       [:p "Please run " [:b "lein figwheel"] " in order to start the compiler!"]]
      [:div.alert.alert-warning
       [:h3 "JavaScript seems to be disabled or missing!"]
       [:p (str "Due to the dynamic nature of Cider-CI "
                "most pages will not work as expected without JavaScript!")]])

    [:div#welcome-message.text-center
     (welcome-message)]
    [:hr]
    [:div#about-message.text-center about-pre-message]
    [:hr]
    [:div#about-release
     [:h1 "Release Notes"]
     [:div.version-info
      [:h2 (navbar.release/navbar-release (atom release-info))]]
     [:div#release-info
      release-notes]]]])

(defn navbar [release]
  [:div.navbar.navbar-default {:role :navigation}
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "/cider-ci/"}
      (navbar.release/navbar-release release)]]
    [:div#nav]]])

(defn html [req]
  (html5
    (head)
    [:body {:class "body-container"
            :data-user (-> req :authenticated-entity
                           (select-keys [:login :is_admin :type
                                         :scope_read :scope_write
                                         :scope_admin_read :scope_admin_write])
                           json/write-str)
            :data-authproviders (->> (get-config) :authentication_providers
                                     (map (fn [[k v]] [k (:name v)]))
                                     (into {}) json/write-str)}
     [:div.container-fluid
      (navbar (-> (cider-ci.utils.self/release) atom))
      (mount-target)
      (include-js (str CONTEXT (cache-buster/cache-busted-path "/js/app.js")))]]))

(defn client-html-response-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (html request)})

(defn dispatch [request handler]
  (cond
    (->> request :route-params :*
         (re-matches #"/api/api-browser.*")) (handler request)
    (->> request :route-params :*
         (re-matches #"/storage/\w+-attachments/.*")) (handler request)
    (->> request :route-params :*
         (re-matches #"/server/ws.*")) (handler request)
    (->> request :route-params :*
         (re-matches #"/session/oauth/\w+/sign-in")) (handler request)
    (and (= (-> request :request-method) :get)
         (= (-> request :accept :mime) :html)) client-html-response-handler
    :else (handler request)))

(defn wrap [handler]
  (fn [request]
    (dispatch request handler)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.anti-forgery)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
