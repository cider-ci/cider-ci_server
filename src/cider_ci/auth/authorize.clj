; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authorize
  (:require
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(def unauthorized-401
  {:status 401
   :headers
   {"WWW-Authenticate"
    (str "Basic realm=\"Cider-CI; "
         "sign in or provide credentials\"")}})

(defn forbidden [options]
  {:status 403
   :body (str "The authorization requirements are not satisfied: " options)})

(defn wrap-require! [handler options]
  (fn [request]
    (logging/debug 'wrap-require! {:handler handler :options options :request request})
    (cond (and (:user options)
               (:authenticated-user request)) (handler request)
          (and (:admin options)
               (-> request :authenticated-user :is_admin)) (handler request)
          (and (:service options)
               (:authenticated-service request)) (handler request)
          (and (:executor options)
               (:authenticated-executor request)) (handler request)
          :else (forbidden options))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


