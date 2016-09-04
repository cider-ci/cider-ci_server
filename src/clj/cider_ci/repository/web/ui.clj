; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.ui
  (:require
    [cider-ci.repository.constants :refer :all]
    [cider-ci.repository.web.shared :refer :all]

    [cider-ci.auth.authorize :as authorize]

    [compojure.core :as cpj :refer [GET defroutes]]
    [compojure.route :refer [not-found resources]]
    [config.core :refer [env]]
    [hiccup.page :refer [include-js include-css html5]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

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

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (str CONTEXT (if (env :dev)
                               "/css/site.css"
                               "/css/site.min.css")))])

(defn loading-page [req]
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-css (str CONTEXT "/assets/font-awesome-4.6.3/css/font-awesome.css"))
     (include-js (str CONTEXT "/js/app.js"))]))

(defn ui-handler [req]
  ((-> loading-page
        (authorize/wrap-require! {:user true})
        ;(wrap-defaults site-defaults)
        ) req))

(defn ui-filter [req]
  "Forwards all accepted routes to the ui-handler,
  otherwise returns nil."
  (apply
    (cpj/routes
      (cpj/GET "/projects/*" _ ui-handler)
      (cpj/GET "/ui*" _ ui-handler)
      (cpj/ANY "*" _  (fn [request] (logging/warn "HTTP 444"  request)
                        {:status 444
                         :body "The repository-ui does not accept this request."}))
      )[req]))

(defn wrap [default-handler]
  (fn [req]
    (logging/debug req)
    (cond
      (= (-> req :accept :mime) :html) (ui-filter req)
      :else (default-handler req))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns 'cider-ci.auth.authorize)
;(debug/debug-ns *ns*)
