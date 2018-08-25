; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication.executor
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])

  (:require
    [cider-ci.server.executors-old]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn tokens [request]
  [(-> request :token-auth presence)
   (-> request :basic-auth :username presence)
   (-> request :basic-auth :password presence)])

(defn authenticate [request]
  (if (:authenticated-entity request)
    request
    (if-let [executor (->> (tokens request)
                           (filter identity)
                           (map cider-ci.server.executors-old/find-executor-by-token)
                           (some identity))]
      (assoc request :authenticated-entity
             (assoc executor
                    :type :executor
                    :authentication-method :token))
      request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (handler (authenticate request))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'authenticate-token-header)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
