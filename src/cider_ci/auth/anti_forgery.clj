; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.anti-forgery
  (:require

    [crypto.random]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(def cookie-name "cider-ci_anti-forgery-token")

(defn cookie [token]
  {:value token
   :http-only true
   :path "/"
   })

(defn- session-authentication? [request]
  (boolean
    (when (-> request :authenticated-user) ; only users can use session auth
      (not (#{"basic-auth"}
             (-> request :authenticated-user :authentication-method))))))

(defn- safe-request? [request]
  (boolean
    ( #{:get :head :options :trace} (-> request :request-method keyword))))

(defn- valid-forgery-protection? [token request]
  (boolean
    (when token
      (when-let [x-csrf-header-token (-> request :headers (get "x-csrf-token"))]
        (= x-csrf-header-token token)))))

(def response-401
  (let [msg (str "Basic realm=\"Cider-CI; "
                 "mutating requests must either authenticate "
                 "with basic authentication or provide a valid "
                 "X-CSRF-TOKEN header!")]
    response-401 {:status 401
                  :body msg
                  :headers {"WWW-Authenticate" msg}}))

(defn process [request handler]
  (let [token (or (-> request :cookies (get cookie-name nil) :value)
                  (crypto.random/base64 32))
        resp (if (and (not (safe-request? request))
                      (not (valid-forgery-protection? token request))
                      (session-authentication? request))
               response-401
               (handler (assoc-in request [:cider-ci_anti-forgery-token] token))
               )]
    (if (and (session-authentication? request)
             (not (valid-forgery-protection? token request)))
      (assoc-in resp [:cookies cookie-name] (cookie token))
      resp)))

(defn wrap [handler]
  (fn [request]
    (process request handler)))

;{"git_url": "https://github.com/cider-ci/cider-ci_deploy.git", "name": "CI Deploy"}

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.anti-forgery)
;(debug/debug-ns *ns*)
