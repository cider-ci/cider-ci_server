(ns cider-ci.api.basic-auth
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as exception]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.middleware.basic-authentication]
    )
  (:import 
    [bcrypt_jruby BCrypt]
    ))

(defonce conf (atom nil))

;##### basic auth ############################################################# 

(defn get-user [login-or-email]
  (with/suppress-and-log-warn
    (or (first (jdbc/query 
                 (:ds @conf)
                 ["SELECT users.* FROM users 
                  INNER JOIN email_addresses ON email_addresses.user_id = users.id 
                  WHERE email_addresses.email_address = ?
                  LIMIT 1" login-or-email]))
        (first (jdbc/query 
                 (:ds @conf)
                 ["SELECT * FROM users 
                  WHERE login_downcased = ?
                  LIMIT 1" (clojure.string/lower-case login-or-email)])))))

(defn authenticated? [login-or-email password]
  (when-let [user (get-user login-or-email)]
    (when (BCrypt/checkpw password  (:password_digest user))
      user)))

(defn add-user-handler [handler]
  (fn [request]
    (logging/debug "ADD USER HERE" {:request request})
    (handler (clojure.set/rename-keys request {:basic-authentication :user} ))))

(defn authenticate-wrapper [handler]
  (ring.middleware.basic-authentication/wrap-basic-authentication 
    (add-user-handler handler) authenticated?))


(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


