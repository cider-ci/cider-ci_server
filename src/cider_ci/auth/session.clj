; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.session
  (:require
    [clj-time.core :as time]
    [cider-ci.utils.duration :as duration]
    [clj-time.format :as time-format]
    [logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [cider-ci.utils.config :as config :refer [get-config]]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [cider-ci.open-session.encryptor :refer [decrypt]]
    [cider-ci.open-session.signature :refer [validate!]]
    [clojure.walk :refer [keywordize-keys]]
    ))


;### Debug ####################################################################

(defn get-user! [user-id]
  (or (first (jdbc/query (rdbms/get-ds)
                         ["SELECT * FROM users
                          WHERE id= ?::UUID" user-id]))
      (throw (IllegalStateException. (str "User for " user-id " not found")))))

(defn get-session-secret []
  (-> (get-config) :session :secret))

(defn validate-session-lifetime! [valid-for-secs issued-at]
  (let [expires-at (time/plus issued-at (time/seconds valid-for-secs))]
    (when (time/before? expires-at (time/now))
      (throw (IllegalStateException. "Session has expired")))))

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
          (validate-session-lifetime! max-session-lifetime-secs issued-at)))
      )))

(defn authenticate-session-cookie [request handler]
  (if-let [services-cookie (-> request keywordize-keys :cookies :cider-ci_services-session :value)]
    (catcher/snatch
      {:return-fn (fn [_] (handler request))}
      (let [session-object (decrypt (get-session-secret) services-cookie)
            user (-> session-object :user_id get-user!)]
        (validate! (-> session-object :signature)
                   (get-session-secret)
                   (-> user :password_digest))
        (validate-expiration! user session-object)
        (when-not (:account_enabled user)
          (throw (IllegalStateException. "Account disabled!")))
        (handler (assoc request :authenticated-user user))))
    (handler request)))

(defn wrap [handler]
  (fn [request]
    (authenticate-session-cookie request handler)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
