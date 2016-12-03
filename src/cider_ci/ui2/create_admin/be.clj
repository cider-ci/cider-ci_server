(ns cider-ci.ui2.create-admin.be
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.web.shared :as shared]

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

(defn- users-insert [row]
  (jdbc/insert! (rdbms/get-ds) :users row))

(defn create-admin [request]
  (Thread/sleep 1000)
  (if (shared/admins?)
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
  (if (or (shared/admins?)
          (= "/create-admin"
             (-> request :route-params :* )))
    (handler request)
    (ring.util.response/redirect
      (str CONTEXT "/create-admin")
      :see-other)))

(defn wrap [handler]
  (cpj/routes
    (cpj/POST "/create-admin" [] #'create-admin)
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


