(ns cider-ci.ui2.session.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.constants :refer [SESSION-COOKIE-KEY]]

    [cider-ci.open-session.encryptor :as encryptor]
    [cider-ci.open-session.signature :as signature]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.utils.ring :refer [delete-session-cookie]]

    [clj-time.core :as time]

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
      delete-session-cookie))

(defn session-secret []
  (-> (get-config) :session :secret))

(defn sign-in-cookie-value [user]
  {:user_id (:id user)
   :issued_at (time/now)
   :signature (signature/create
                (session-secret) (or (:password_digest user) ""))})

(defn encrypted-sign-in-cookie-value [user]
  (encryptor/encrypt (session-secret) (sign-in-cookie-value user)))

(defn sign-in! [user redirect-target]
  (-> (ring.util.response/redirect
        redirect-target
        :see-other)
      (assoc-in [:cookies (str SESSION-COOKIE-KEY)]
                {:value (encrypted-sign-in-cookie-value user)
                 :http-only true :path "/"
                 :max-age (* 10 356 24 60 60)})))

;(debug/debug-ns 'cider-ci.open-session.signature)
;(debug/debug-ns *ns*)

