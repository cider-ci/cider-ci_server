; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.session
  (:require
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

(def session-cookie-key :cider-ci_services-session)

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

(defn encrypted-session-cookie-value [request]
  (-> request keywordize-keys :cookies session-cookie-key :value))

(defn decrypted-session [encrypted-session-cookie-value]
  (when encrypted-session-cookie-value
    (decrypt (get-session-secret) encrypted-session-cookie-value)))

(defn session-user [session-object]
  (-> session-object :user_id get-user!
      (assoc :auhtentication-method "session")))

(defn authenticated-user [request]
  "Returns the authenticated user or nil."
  (catcher/snatch
    {}
    (when-let [session-object  (-> request encrypted-session-cookie-value
                                   decrypted-session)]
      (when-let [user (session-user session-object)]
        (validate! (-> session-object :signature)
                   (get-session-secret)
                   (or (-> user :password_digest) ""))
        (validate-expiration! user session-object)
        (when-not (:account_enabled user)
          (throw (IllegalStateException. "Account disabled!")))
        user))))

(defn authenticate-session-cookie [request handler]
  (if-let [user (authenticated-user request)]
    (handler (assoc request :authenticated-user user))
    (handler request)))

(defn wrap [handler & {:keys [anti-forgery] :or {anti-forgery true}}]
  "Adds :authenticated-user to the request if the session is intact.
  Does nothing to the request otherwise."
  (let [handler (if anti-forgery
                  (anti-forgery/wrap handler)
                  handler)]
    (fn [request] (authenticate-session-cookie request handler))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
