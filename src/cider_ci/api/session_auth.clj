(ns cider-ci.api.session-auth
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
    )
  (:import 
    [org.jruby.embed InvokeFailedException ScriptingContainer]
    ))


(defonce conf (atom nil))

(.getLoadPaths (ScriptingContainer.))

(defn run-jruby [ruby-code]
  (.runScriptlet (ScriptingContainer.) ruby-code))


(defn ruby-hmac-signature-ok? [message secret check-signature]
  (let [json_aray (json/write-str [message,secret])
        ruby-code (str "require 'openssl'; "
                       "require 'json'; "
                       "message, secret= JSON.parse('" json_aray "'); "
                       "digest = OpenSSL::Digest.new('sha1'); "
                       "OpenSSL::HMAC.hexdigest(digest, secret, message)")
        signature (run-jruby ruby-code)]
    (= check-signature signature)))

(def ruby-hmac-signature-ok?-memoized (memoize ruby-hmac-signature-ok?))

(defn get-user [user-id]
  (first (jdbc/query (:ds @conf)
              ["SELECT * FROM users 
                WHERE id= ?::UUID" user-id])))

(defn authenticate-session-cookie [request]
  (try 
    (logging/debug (:cookies request))
    (when-let 
      [session-cookie (with/suppress-and-log-debug 
                        (-> request :cookies (clojure.walk/keywordize-keys)
                            :cider-ci_services-session :value (json/read-str :key-fn keyword)))]
      (logging/debug session-cookie)
      (when-let [user (get-user (:user_id session-cookie))]
        (logging/debug user)
        (if (ruby-hmac-signature-ok?-memoized 
              (:id user) (:password_digest user) (:signature session-cookie))
          user
          (do 
            (logging/warn "VALIDATING SESSION FAILED " {:user user :session-cookie session-cookie})
            nil))))
    (catch Exception e
      (logging/warn (exception/stringify e)))))


(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

