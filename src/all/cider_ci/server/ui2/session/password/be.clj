(ns cider-ci.server.ui2.session.password.be
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.ui2.session.shared :refer [sign-in!]]
    [cider-ci.server.ui2.constants :refer [CONTEXT]]

    [cider-ci.auth.authentication.password]
    [cider-ci.open-session.bcrypt :refer [checkpw]]
    [cider-ci.utils.core :refer [presence]]

    [ring.util.codec :refer [url-encode]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn password-valid? [user password]
  (checkpw password (:password_digest user)))


(defn build-query-string [m]
  (->> m (map (fn [[k v]]
                (str (-> k str url-encode) "="
                     (-> v str url-encode))))
       (clojure.string/join "&")))

(defn sign-in [request]
  (if-let [user (cider-ci.auth.authentication.password/get-user
                  (-> request :form-params :login))]
    (if (:account_enabled user)
      (if (password-valid? user (-> request :form-params :password))
        (sign-in! user (or (-> request :form-params :path presence) (str CONTEXT "/")))
        (ring.util.response/redirect
          (str CONTEXT "/session/password/sign-in?"
               (->> {:url (-> request :form-params :url presence)
                     :error-message "Sign-in failed: the password is wrong!"}
                    build-query-string)) :see-other))
      (ring.util.response/redirect
        (str CONTEXT "/session/password/sign-in?"
             (->> {:url (-> request :form-params :url presence)
                   :error-message "Sign-in failed: the account is disabled!"}
                  build-query-string)) :see-other))
    (ring.util.response/redirect
      (str CONTEXT "/session/password/sign-in?"
           (->> {:url (-> request :form-params :url presence)
                 :error-message "Sign-in failed: no matching account was found!"}
                build-query-string)) :see-other)))


;#### debug ###################################################################
;(debug/debug-ns *ns*)
