; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.session
  (:require
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

; TODO check session expiration

(defn authenticate-session-cookie [request handler]
  (if-let [services-cookie (-> request keywordize-keys :cookies :cider-ci_services-session :value)]
    (try (logging/debug services-cookie)
         (let [session-object (decrypt (get-session-secret) services-cookie)
               user (-> session-object :user_id get-user!)]
           (validate! (-> session-object :signature)
                      (get-session-secret)
                      (-> user :password_digest))
           (when-not (:account_enabled user)
             (throw (IllegalStateException. "Account disabled!")))
           (handler (assoc request :authenticated-user user)))
         (catch Exception e
           (logging/warn e)
           (handler request)))
    (handler request)))

(defn wrap [handler]
  (fn [request]
    (authenticate-session-cookie request handler)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
