; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ui2.session.be
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]

    [cider-ci.auth.http-basic]
    [cider-ci.auth.session]
    [cider-ci.open-session.bcrypt :refer [checkpw]]
    [cider-ci.open-session.encryptor :as encryptor]
    [cider-ci.open-session.signature :as signature]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.core :refer [presence]]

    [ring.util.codec :refer [url-encode]]
    [clj-time.core :as time]
    [ring.util.response]
    [compojure.core :as cpj]
    [clj-http.client :as http-client]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn sign-out [request]
  (-> (ring.util.response/redirect
        (or (-> request :form-params :url presence)
            (str CONTEXT "/"))
        :see-other)
      (assoc-in [:cookies (name cider-ci.auth.session/session-cookie-key) ]
                {:value ""
                 :path "/"
                 :max-age 0})))

(defn session-secret []
  (-> (get-config) :session :secret))

(defn sign-in-cookie-value [user]
  {:user_id (:id user)
   :issued_at (time/now)
   :signature (signature/create
                (session-secret) (:password_digest user))})

(defn encrypted-sign-in-cookie-value [user]
  (encryptor/encrypt (session-secret) (sign-in-cookie-value user)))

(defn password-valid? [user password]
  (checkpw password (:password_digest user)))

(defn sign-in! [user request]
  (-> (ring.util.response/redirect
        (or (-> request :form-params :path presence)
            (str CONTEXT "/")) :see-other)
      (assoc-in [:cookies (name cider-ci.auth.session/session-cookie-key)]
                {:value (encrypted-sign-in-cookie-value user)
                 :http-only true
                 :path "/"
                 :max-age (* 10 356 24 60 60)})))

(defn build-query-string [m]
  (->> m (map (fn [[k v]]
                (str (-> k str url-encode) "="
                     (-> v str url-encode))))
       (clojure.string/join "&")))

(defn password-sign-in [request]
  (if-let [user (cider-ci.auth.http-basic/get-user
                  (-> request :form-params (get "login" nil)))]
    (if (:account_enabled user)
      (if (password-valid? user (-> request :form-params (get "password" nil)))
        (sign-in! user request)
        (ring.util.response/redirect
          (str CONTEXT "/session/password-sign-in?"
               (->> {:url (-> request :form-params :url presence)
                     :error-message "Sign-in failed: the password is wrong!"}
                    build-query-string)) :see-other))
      (ring.util.response/redirect
        (str CONTEXT "/session/password-sign-in?"
             (->> {:url (-> request :form-params :url presence)
                   :error-message "Sign-in failed: the account is disabled!"}
                  build-query-string)) :see-other))
    (ring.util.response/redirect
      (str CONTEXT "/session/password-sign-in?"
           (->> {:url (-> request :form-params :url presence)
                 :error-message "Sign-in failed: no matching account was found!"}
                build-query-string)) :see-other)))

(defn wrap-routes [default-handler]
  (cpj/routes
    (cpj/POST "/session/sign-out" _ #'sign-out)
    (cpj/POST "/session/password-sign-in" _ #'password-sign-in)
    (cpj/ANY "*" _ default-handler)
    ))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'password-sign-in)
;(debug/debug-ns 'cider-ci.open-session.bcrypt)
;(debug/debug-ns 'cider-ci.open-session.signature)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)

