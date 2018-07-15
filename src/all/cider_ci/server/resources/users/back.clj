(ns cider-ci.server.resources.users.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.server.resources.user.back :as user]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(def users-base-query
  (-> (sql/select :id, :primary_email_address)
      (sql/from :users)
      (sql/order-by :primary_email_address)))

(defn set-per-page-and-offset
  ([query {{per-page :per-page page :page} :query-params}]
   (logging/info 'per-page per-page)
   (when (or (-> per-page presence not)
             (-> per-page integer? not)
             (> per-page 1000)
             (< per-page 1))
     (throw (ex-info "The query parameter per-page must be present and set to an integer between 1 and 1000."
                     {:status 422})))
   (when (or (-> page presence not)
             (-> page integer? not)
             (< page 0))
     (throw (ex-info "The query parameter page must be present and set to a positive integer."
                     {:status 422})))
   (set-per-page-and-offset query per-page page))
  ([query per-page page]
   (-> query
       (sql/limit per-page)
       (sql/offset (* per-page (- page 1))))))

(defn term-fitler [query request]
  (if-let [term (-> request :query-params :term presence)]
    (-> query
        (sql/merge-where [:or
                          ["%" (str term) :searchable]
                          ["~~*" :searchable (str "%" term "%")]]))
    query))


(defn admins-filter [query request]
  (let [is-admin (-> request :query-params :is_admin)]
    (case is-admin
      ("true" true) (sql/merge-where query [:= :is_admin true])
      query)))

(defn users-query [request]
  (-> users-base-query
      (set-per-page-and-offset request)
      (term-fitler request)
      (admins-filter request)
      sql/format))

(defn users [request]
  (when (= :json (-> request :accept :mime))
    {:body
     {:users
      (jdbc/query (:tx request) (users-query request))}}))

(def routes
  (cpj/routes
    (cpj/GET (path :users) [] #'users)
    (cpj/POST (path :users) [] #'user/routes)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
