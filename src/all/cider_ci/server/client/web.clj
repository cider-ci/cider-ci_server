; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.client.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.client.ui.navbar.release :as navbar.release]
    [cider-ci.server.client.constants :refer [CONTEXT]]

    [cider-ci.server.client.web.shared :as web.shared :refer [dynamic]]
    [cider-ci.server.client.welcome-page.be :as welcome-page]
    [cider-ci.server.client.root :as root]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]

    [cider-ci.env]
    [hiccup.page :refer [include-js include-css html5]]
    [clojure.data.json :as json]
    [clojure.walk :refer [keywordize-keys]]
    [clj-time.core :as time]
    [clojure.data :as data]
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
                     (if (= cider-ci.env/env :dev)
                       "/css/site.css"
                       "/css/site.min.css")))])


(defn mount-target []
  [:div#app
   [:div.container-fluid
    (if (= cider-ci.env/env :dev)
      [:div.alert.alert-warning
       [:h3 "ClojureScript has not been compiled!"]
       [:p "This page depends on JavaScript!"]
       [:p "Please run " [:b "lein figwheel"] " in order to start the compiler!"]]
      [:div.alert.alert-warning
       [:h3 "JavaScript seems to be disabled or missing!"]
       [:p (str "Due to the dynamic nature of Cider-CI "
                "most pages will not work as expected without JavaScript!")]])
    ]])

(defn navbar [release]
  [:div.navbar.navbar-default {:role :navigation}
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "/cider-ci/client/"}
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
      (include-css (str "https://maxcdn.bootstrapcdn.com/"
                        "font-awesome/4.6.3/css/font-awesome.min.css"))
      (include-js (str CONTEXT "/js/app.js"))]]))


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
         (= (-> request :accept :mime) :html)) {:status 200 :body (html request)}
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
(debug/debug-ns *ns*)
