; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.authentication.session
  (:require
    [cider-ci.constants :refer [SESSION-COOKIE-KEY]]
    [cider-ci.utils.ring :refer [delete-session-cookie]]

    [cider-ci.auth.anti-forgery :as anti-forgery]

    [cider-ci.open-session.encryptor :refer [decrypt]]
    [cider-ci.open-session.signature :refer [validate!]]

    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.duration :as duration]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.java.jdbc :as jdbc]
    [clojure.walk :refer [keywordize-keys]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn session-error-page [exception request]
  (-> {:status 477
       :body (.getMessage exception)}
      delete-session-cookie))

(defn user! [user-id]
  (or (first (jdbc/query (rdbms/get-ds)
                         ["SELECT * FROM users
                          WHERE id= ?::UUID" user-id]))
      (throw (IllegalStateException. (str "User for " user-id " not found")))))

(defn validate-session-lifetime! [valid-for-secs issued-at]
  (let [expires-at (time/plus issued-at (time/seconds valid-for-secs))]
    (when (time/before? expires-at (time/now))
      (throw (IllegalStateException. "Session has expired! Please reload and sign in.")))))

(defn validate-expiration! [user session-object]
  (let [issued-at-str (-> session-object :issued_at)]
    (when (clojure.string/blank? issued-at-str)
      (throw (IllegalStateException. "Session issued-at must not be blank")))
    (let [issued-at (-> issued-at-str time-format/parse)]
      (when-let [max-session-lifetime (:max_session_lifetime user)]
        (let [max-session-lifetime-secs (duration/parse-string-to-seconds max-session-lifetime)]
          (validate-session-lifetime! max-session-lifetime-secs issued-at)))
      (let [max-session-lifetime (-> (get-config) :session :max_lifetime)]
        (let [max-session-lifetime-secs (duration/parse-string-to-seconds max-session-lifetime)]
          (validate-session-lifetime! max-session-lifetime-secs issued-at))))))

(defn validate-user-account! [user]
  (when-not (:account_enabled user)
    (throw (IllegalStateException. "Account disabled!"))))

(defn authenticated-user [session-object]
  (let [user (-> session-object :user_id user!)]
    (validate-expiration! user session-object)
    (validate-user-account! user)
    (assoc user
           :type :user
           :authentication-method :session
           :scope_read true
           :scope_write true
           :scope_admin_read (:is_admin user)
           :scope_admin_write (:is_admin user))))

(defn session-cookie [request]
  (-> request :cookies keywordize-keys SESSION-COOKIE-KEY :value))

(defn session-secret [] (-> (get-config) :session :secret))

(defn authenticate [request handler]
  (if-let [cookie (session-cookie request)]
    (catcher/snatch
      {:level :debug
       :return-fn (fn [e] (session-error-page e request))}
      (handler
        (assoc request
               :authenticated-entity
               (->> cookie (decrypt (session-secret)) authenticated-user))))
    (handler request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (authenticate request handler)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
