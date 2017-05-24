; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.auth.authentication.token-header
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.users.api-tokens.core :refer [hash-string]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(defn add-token [request]
  (or (when-let [auth-header (-> request :headers :authorization)]
        (when-let [token (->> auth-header
                              (re-find #"(?i)^token\s+(.*)$")
                              last presence)]
          (catcher/snatch
            {:level :debug
             :return-expr {:status 422
                           :body "Failure extracting token auth value"}}
            (assoc request :token-auth token))))
      request))

(defn extract [request handler]
  (-> request add-token handler))

(defn wrap-extract [handler]
  "Extracts information from the \"Authorization: Token...\" header and adds
  :token-auth TOKEN-VALUE.  Continues if no such header is found.  Catches and
  returns 422 if the header is found but extraction fails. "
  (fn [request] (extract request handler)))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'authenticate-token-header)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
