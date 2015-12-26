; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.routing
  (:require
    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    ))


(defn wrap-prefix
  "Check for prefix match. Pass on and add :contex, or return 404 if it doesn't match."
  [default-handler prefix]
  (cpj/routes
    (cpj/context prefix []
                 (cpj/ANY "*" request default-handler))
    (cpj/ANY "*" [] {:status 404})))


(defn wrap-log-exception [handler]
  (fn [request]
    (catcher/wrap-with-log-error
      (handler request))))


;### shutdown #################################################################

(defn shutdown [request]
  (if (-> request :authenticated-service :username boolean)
    (do (future (System/exit 0))
      {:status 204 })
    {:status 403 :body ""}))

(defn wrap-shutdown [default-handler]
  (cpj/routes
    (cpj/POST "/shutdown" request #'shutdown)
    (cpj/ANY "*" request default-handler)))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
