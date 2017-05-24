; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication.password
  (:refer-clojure :exclude [str keyword])

  (:require
    [cider-ci.open-session.bcrypt :refer [checkpw]]
    [cider-ci.open-session.encoder :refer [decode]]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.data.codec.base64 :as base64]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :refer [lower-case]]
    [crypto.random]
    [pandect.algo.sha1 :refer [sha1-hmac]]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  )

(defn get-user [login-or-email]
  (catcher/snatch {}
    (when-let [ds (rdbms/get-ds)]
      (or (first (jdbc/query
                   ds
                   ["SELECT users.* FROM users
                    INNER JOIN email_addresses ON email_addresses.user_id = users.id
                    WHERE lower(email_addresses.email_address) = lower(?)
                    LIMIT 1" (lower-case login-or-email)]))
          (first (jdbc/query
                   ds
                   ["SELECT * FROM users
                    WHERE lower(login) = lower(?)
                    LIMIT 1" (lower-case login-or-email)]))))))

(defn find-authenticated-user [username password]
  (when-let [user (get-user username)]
    (when (and (:account_enabled user)
               (checkpw password (:password_digest user)))
      (assoc user
             :type :user
             :authentication-method :basic-auth
             :scope_read true
             :scope_write true
             :scope_admin_read (:is_admin user)
             :scope_admin_write (:is_admin user)))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn service-entity [username]
  {:name username
   :type :service
   :authentication-method :basic-auth
   :scope_read true
   :scope_write true
   :scope_admin_read true
   :scope_admin_write true})

(defn password-matches? [username password]
  (or (when-let [settings-basic-auth-password
                 (-> (get-config) :basic_auth :password presence)]
        (or (= password settings-basic-auth-password)
            (= password (sha1-hmac username settings-basic-auth-password))))
      (when-let [settings-master-secret (-> (get-config) :secret presence)]
        (or (= password settings-master-secret)
            (= password (sha1-hmac username settings-master-secret))))))

(defn find-authenticated-service [username password]
  (when (password-matches? username password)
    (service-entity username)))

(defn find-executor [executor-name]
  (first (jdbc/query
           (rdbms/get-ds)
           ["SELECT * FROM executors WHERE name = ?"
            executor-name])))

(defn find-authenticated-executor [username password]
  (when-let [executor (find-executor username)]
    (when (password-matches? username password)
      (assoc executor
             :type :executor))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn authenticate [request handler]
  (handler
    (if-let [{username :username password :password} (:basic-auth request)]
      (if-let [authenticated-entity (or (find-authenticated-executor username password)
                                        (find-authenticated-service username password)
                                        (find-authenticated-user username password))]
        (assoc request :authenticated-entity authenticated-entity)
        request)
      request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (authenticate request handler)))


;### Debug ####################################################################
(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
