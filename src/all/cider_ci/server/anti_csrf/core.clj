(ns cider-ci.server.anti-csrf.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.constants :as constants]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]

    )
  (:import
    [java.util UUID]
    ))

(defn http-safe? [request]
  (boolean (constants/HTTP_SAVE_METHODS
             (-> request :request-method))))

(def http-unsafe? (complement http-safe?))

(defn session? [request]
  (= :session (-> request :authenticated-entity
                  :authentication-method)))

(defn x-csrf-token! [request]
  (or (-> request :headers :x-csrf-token presence)
      (-> request :headers (get "x-csrf-token" nil))
      (-> request :form-params :csrf-token)
      (throw (ex-info "The x-csrf-token is not set!" {:status 403}))))

(defn anti-csrf-token [request]
  (-> request :cookies
      (get constants/ANTI_CRSF_TOKEN_COOKIE_NAME nil)
      :value presence))

(defn wrap [handler]
  (fn [request]
    (let [anti-csrf-token (anti-csrf-token request)]
      (when (and (http-unsafe? request) (session? request))
        (when-not (presence anti-csrf-token)
          (throw (ex-info "The anti-csrf-token cookie value is not set." {:status 403})))
        (when-not (= anti-csrf-token (x-csrf-token! request))
          (throw (ex-info (str "The x-csrf-token is not equal to the "
                               "anti-csrf cookie value.") {:status 403}))))
      (let [response (handler request)]
        (if (and (session? request)
                 (not anti-csrf-token))
          (assoc-in response [:cookies constants/ANTI_CRSF_TOKEN_COOKIE_NAME]
                    {:value (str (UUID/randomUUID))
                     :http-only false
                     :path "/"
                     :secure false})
          response)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
