; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.http-basic
  (:require
    [cider-ci.open-session.bcrypt :refer [checkpw]]
    [cider-ci.open-session.encoder :refer [decode]]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.data.codec.base64 :as base64]
    [clojure.java.jdbc :as jdbc]
    [clojure.string :refer [lower-case]]
    [pandect.algo.sha1 :refer [sha1-hmac]]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


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

(defn authenticate-user [login-or-email password]
  (when-let [user (get-user login-or-email)]
    (when (and (:account_enabled user)
               (checkpw password (:password_digest user)))
      user)))

(defn password-matches [password username]
  (when-let [secret (:secret (get-config))]
    (and username
         (= password (sha1-hmac username secret)))))

(defn create-password
  ([username secret]
   (sha1-hmac username secret))
  ([username]
   (if-let [secret (:secret (get-config))]
     (create-password username secret)
     (throw (IllegalStateException. "The secret is not set")))))

(defn get-executor [executor-name]
  (first (jdbc/query
           (rdbms/get-ds)
           ["SELECT * FROM executors WHERE id::TEXT = ?"
            executor-name])))

(defn authenticate-executor [executor-name password-digest]
  (when (password-matches password-digest executor-name)
    (get-executor executor-name)))

(defn- authenticate-role [request roles]
  (let [request (atom request)]
    (if-let [ba (:basic-auth-request @request)]
      (let [{username :username password :password} ba]
        (logging/debug [ba,username,password])
        (when (:service roles)
          (when (password-matches password username)
            (swap! request
                   (fn [request username]
                     (assoc request :authenticated-service {:username username}))
                   username)))
        (when (:user roles)
          (when-let [user (authenticate-user username password)]
            (swap! request
                   (fn [request user]
                     (assoc request :authenticated-user user))
                   user)))
        (when (:executor roles)
          (when-let [executor (authenticate-executor username password)]
            (swap! request
                   (fn [request executor]
                     (assoc request :authenticated-executor executor))
                   executor)))))
    @request))

(defn- authenticate-app-or-user [request]
  (if-let [ba (:basic-auth-request request)]
    (let [{username :username password :password} ba]
      (logging/debug [ba,username,password])
      (if (= password (-> (get-config) :basic_auth  :password))
        (assoc request :authenticated-service {:username username})
        (if-let [user (authenticate-user username password)]
          (assoc request :authenticated-user user)
          request)))
    request))

;##########################################################

(defn- decode-base64
  [^String string]
  (apply str (map char (base64/decode (.getBytes string)))))

(defn- extract-and-add-basic-auth-properties
  "Extracts information from the \"authorization\" header and
  adds a :basic-auth-request key to the request with the value
  {:name name :password password}."
  [request]
  (if-let [auth-header ((:headers request) "authorization")]
    (catcher/snatch
      {:return-expr request}
      (let [decoded-val (decode-base64 (last (re-find #"^Basic (.*)$" auth-header)))
            [name password] (clojure.string/split (str decoded-val) #":" 2)]
        (assoc request :basic-auth-request {:username name :password password})))
    request))

;##########################################################

(defn wrap-extract
  "Extracts information from the \"authorization\" header and
  adds  :basic-auth-request {:name name :password password}
  to the request if extraction succeeded. Leaves the request as
  is otherwise."
  [handler]
  (fn [request]
    (if-let [request-with-auth
             (catcher/snatch
               {:level :debug}
               (extract-and-add-basic-auth-properties request))]
      (handler request-with-auth)
      (handler request))))

;##########################################################

(defn wrap
  ([handler roles]
   (fn [request]
     (if-let [modified-request
              (catcher/snatch
                {} (-> request
                       extract-and-add-basic-auth-properties
                       (authenticate-role roles)))]
       (handler modified-request)
       (handler request)))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)



