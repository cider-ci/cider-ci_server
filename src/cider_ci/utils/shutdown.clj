; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.shutdown
  (:require
    [cider-ci.auth.authorize :as authorize]

    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]

    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    ))

(defn- shutdown [request]
  (if (-> request :authenticated-service :username boolean)
    (do (future (Thread/sleep 250)
                (System/exit 0))
        {:status 204})
    {:status 403 :body ""}))

(defn wrap [default-handler]
  (cpj/routes
    (cpj/POST "/shutdown" _
              (authorize/wrap-require!
                #'shutdown {:service true}))
    (cpj/ANY "*" request default-handler)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
