(ns cider-ci.server.html
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.env :refer [env]]
    [cider-ci.server.resources.user.back :as user]
    [cider-ci.utils.http-resources-cache-buster :as cache-buster :refer [wrap-resource]]
    [cider-ci.utils.json-protocol :refer [to-json]]
    [cider-ci.utils.url :as url]

    [clojure.java.jdbc :as jdbc]
    [hiccup.page :refer [include-js html5]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn include-site-css []
  (hiccup.page/include-css
    (cache-buster/cache-busted-path "/css/site.css")))

(defn include-font-css []
  (hiccup.page/include-css
    "/css/fontawesome-free-5.0.9/css/fontawesome-all.min.css"))

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   (include-site-css)
   (include-font-css)])

(defn user-data [request]
  (url/encode
    (to-json
      (when-let [user-id (-> request :authenticated-entity :user_id)]
        (->> (user/user-query user-id)
             (jdbc/query (:tx request)) first)))))

(defn settings-data [request]
  (url/encode
    (to-json
      (-> request
          :settings
          (select-keys
            [:shibboleth_enabled
             :shibboleth_login_path])))))

(defn not-found-handler [request]
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body (html5
           (head)
           [:body
            {:data-user (user-data request)
             :data-settings (settings-data request)}
            [:div.container-fluid
             [:h1.text-danger "Error 404 - Not Found"]]])})

(defn html-handler [request]
  {:headers {"Content-Type" "text/html"}
   :body (html5
           (head)
           [:body
            {:data-user (user-data request)
             :data-settings (settings-data request)}
            [:div#app.container-fluid
             [:div.alert.alert-warning
              [:h1 "Cider-CI 5"]
              [:p "This application requires Javascript."]]]
            (hiccup.page/include-js
              (cache-buster/cache-busted-path "/js/app.js"))])})


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
