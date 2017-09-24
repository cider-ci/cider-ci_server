; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.client.web.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.client.constants :refer [CONTEXT]]
    [cider-ci.server.client.ui.navbar.release :as navbar.release]
    [cider-ci.server.client.root :as root]

    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.self]
    [cider-ci.utils.config :as config :refer [get-config]]

    [clojure.java.jdbc :as jdbc]
    [hiccup.page :refer [include-js include-css html5]]
    [config.core :refer [env]]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (str CONTEXT (if (env :dev)
                               "/css/site.css"
                               "/css/site.min.css")))])


;; dynamic pages

(defn mount-target []
  [:div#app
   [:div.container-fluid
    (if (env :dev)
      [:div.alert.alert-warning
       [:h3 "ClojureScript has not been compiled!"]
       [:p "This page depends on JavaScript!"]
       [:p "Please run " [:b "lein figwheel"] " in order to start the compiler!"]]
      [:div.alert.alert-warning
       [:h3 "JavaScript seems to be disabled or missing!"]
       [:p (str "Due to the dynamic nature of Cider-CI "
                "most pages will not work as expected without JavaScript!")]])
    (root/page)]])

(defn navbar [release]
  [:div.navbar.navbar-default {:role :navigation}
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "/cider-ci/client/"}
      (navbar.release/navbar-release release)]]
    [:div#nav]]])

(defn dynamic [req]
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


;#### debug ###################################################################

;(debug/debug-ns *ns*)
