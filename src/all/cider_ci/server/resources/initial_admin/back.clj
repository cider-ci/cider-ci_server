(ns cider-ci.server.resources.initial-admin.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.resources.user.back :refer [password-hash]]

    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.rdbms :refer [ds]]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [ring.util.response :refer [redirect]]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug])
  (:import
    [java.util UUID]
    ))

(defn some-admin? [tx]
  (->> ["SELECT true AS has_admin FROM users WHERE is_admin = true"]
       (jdbc/query tx ) first :has_admin boolean))

(defn prepare-data [data tx]
  (-> data
      (select-keys [:primary_email_address])
      (assoc :is_admin true
             :password_hash (password-hash (:password data) tx)
             :id (UUID/randomUUID))))

(defn create-admin [data tx]
  (let [email-address (:primary_email_address data)
        email-address-row (jdbc/insert! tx :email_addresses 
                                        {:user_id nil
                                         :email_address email-address})
        admin (first (jdbc/insert! tx :users data))
        admin-id (:id admin)]
    (jdbc/update! tx :email_addresses {:user_id admin-id}
                  ["email_address = ?" email-address])
    admin))

(defn create-initial-admin
  ([{tx :tx form-params :form-params data :body}]
   (create-initial-admin (if (empty? form-params)
                           data form-params) tx))
  ([data tx]
   (if (some-admin? tx)
     {:status 403
      :body "An admin user already exists!"}
     (when-let [user (-> data (prepare-data tx) (create-admin tx))]
       (redirect (path :admin) :see-other)))))

(def routes
  (cpj/routes
    (cpj/POST (path :initial-admin) [] create-initial-admin)))

(defn wrap
  ([handler] (fn [request] (wrap handler request)))
  ([handler request]
   (if (or (not= (-> request :accept :mime) :html)
           (= (:handler-key request) :initial-admin)
           (some-admin? (:tx request)))
     (handler request)
     (redirect (path :initial-admin) :see-other))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
