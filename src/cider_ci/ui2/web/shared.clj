; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ui2.web.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.ui.navbar :as navbar]

    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.self]

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

(defn admins? []
  (->> ["SELECT true AS exists FROM users
        WHERE is_admin = true limit 1"]
       (jdbc/query (rdbms/get-ds))
       first
       :exists
       boolean))

;; static pages

(defn static [req handler]
  (html5
    (head)
    [:body
     [:div.container-fluid
      (navbar/navbar (-> (cider-ci.utils.self/release) atom)
                     (-> req :authenticated-user atom)
                     (-> req :uri atom)
                     (-> req :route-params :* atom))]
     [:div.container-fluid
      (handler req)]
     (include-css "https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css")
     (include-js (str CONTEXT "/js/app.js"))]))

(defn wrap-static [handler]
  (fn [request]
    (static request handler)))


;; dynamic pages

(def mount-target
  [:div#app
   [:div.container-fluid
    (if (env :dev)
      [:div.alert.alert-warning
       [:h3 "ClojureScript has not been compiled!"]
       [:p "This page depends on JavaScript!"]
       [:p "Please run " [:b "lein figwheel"] " in order to start the compiler!"]]
      [:div.alert.alert-danger
       [:h3 "JavaScript required!"]
       [:p "This page depends on JavaScript which seems to be disabled!"]])]])

(defn dynamic [req]
  (html5
    (head)
    [:body {:class "body-container"}
     [:div.container-fluid
      (navbar/navbar (-> (cider-ci.utils.self/release) atom)
                     (-> req :authenticated-user atom)
                     (-> req :uri atom)
                     (-> req :route-params :* atom))
      mount-target
      ;(include-css (str CONTEXT "/assets/font-awesome-4.6.3/css/font-awesome.css"))
      (include-css "https://maxcdn.bootstrapcdn.com/font-awesome/4.6.3/css/font-awesome.min.css")
      ;(include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js")
      (include-js (str CONTEXT "/js/app.js"))
      ]]))


;#### debug ###################################################################

;(debug/debug-ns *ns*)

