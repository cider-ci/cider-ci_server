(ns cider-ci.create-initial-admin.web
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.open-session.bcrypt :as bcrypt]

    [clojure.java.jdbc :as jdbc]
    [cider-ci.utils.rdbms :as rdbms]

    [compojure.core :as cpj]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn some-admin-exists? []
  (->> ["SELECT true AS exists FROM users
        WHERE is_admin = true limit 1"]
       (jdbc/query (rdbms/get-ds))
       first :exists boolean))

(defn- users-insert [row]
  (jdbc/insert! (rdbms/get-ds) :users row))

(defn create-admin [request]
  (Thread/sleep 1000)
  (if (some-admin-exists?)
    {:status 409
     :body "An admin exists already!"}
    (if (->> {:login (-> request :body :login)
              :password_digest (-> request :body :password bcrypt/hashpw)
              :is_admin true}
             users-insert first presence boolean)
      {:status 201
       :body "created!"}
      {:status 422
       :body "failed"})))

(defn- redirect [request handler]
  (if (or (some-admin-exists?)
          (= "/create-initial-admin" (-> request :route-params :* )))
    (handler request)
    (ring.util.response/redirect
      (str "/cider-ci/create-initial-admin")
      :see-other)))

(defn wrap [handler]
  (cpj/routes
    (cpj/POST "/create-initial-admin" [] #'create-admin)
    (cpj/GET "*" [] (fn [req] (redirect req handler)))
    (cpj/ANY "*" [] handler)))

;(defn wrap [handler] (fn [request] (redirect request handler)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.open-session.bcrypt)
;(debug/debug-ns *ns*)
