; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.routing
  (:require
    [cider-ci.auth.authorize :as authorize]

    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]

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


(defn wrap-exception [handler]
  (fn [request]
    (catcher/snatch
      {:level :warn
       :return-fn (fn [e]
                    {:status 500
                     :body (thrown/stringify e)})}
      (handler request))))

