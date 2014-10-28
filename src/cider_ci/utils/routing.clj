; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.routing
  (:require 
    [cider-ci.utils.with :as with]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    ))


(defn wrap-debug-logging 
  "Wraps a handler with debug logging of request and response to the given namespace."
  [handler ns]
  (fn [request]
    (let [wrap-debug-logging-level (or (:wrap-debug-logging-level request) 0 )]
      (logging/log ns :debug nil (print-str  "wrap-debug-logging " wrap-debug-logging-level " request: " request))
      (let [response (handler (assoc request :wrap-debug-logging-level (+ wrap-debug-logging-level 1)))]
        (logging/log ns :debug nil (print-str  "wrap-debug-logging " wrap-debug-logging-level " response: " response))
        response))))

(defn wrap-prefix 
  "Check for prefix match. Pass on and add :contex, or return 404 if it doesn't match."
  [default-handler prefix]
  (cpj/routes
    (cpj/context prefix []
                 (cpj/ANY "*" request default-handler))
    (cpj/ANY "*" [] {:status 404})))


(defn wrap-log-exception [handler]
  (fn [request]
    (with/logging
      (handler request))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
