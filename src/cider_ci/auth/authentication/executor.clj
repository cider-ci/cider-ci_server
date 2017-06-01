; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication.executor
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])

  (:require
    [cider-ci.executors]
    ))

(defn authenticate [request]
  (if (:authenticated-entity request)
    request
    (if-let [token (:token-auth request)]
      (if-let [executor (cider-ci.executors/find-executor-by-token token)]
        (assoc request :authenticated-entity
               (assoc executor
                      :type :executor
                      :authentication-method :token))
        request)
      request)))

(defn wrap-authenticate [handler]
  (fn [request]
    (handler (authenticate request))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'authenticate-token-header)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
