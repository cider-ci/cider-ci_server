; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;
(ns cider-ci.executor.http
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])

  (:require
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.config :as config :refer [get-config merge-into-conf]]
    [cider-ci.utils.shutdown :as shutdown]
    [cider-ci.utils.routing :as routing]

    [compojure.core :as cpj]
    [ring.middleware.defaults]
    [ring.middleware.json]
    ))


(defn shutdown [_]
  (do (future (Thread/sleep 250)
              (System/exit 0))
      {:status 204}))


(defn pong [_]
  {:status 200
   :body "pong"})


(def routes
  (cpj/routes
    (cpj/POST "/shutdown" _ shutdown)
    (cpj/GET "/ping" _ pong)
    (cpj/ANY "*" _ {:status 404})))

(def main-handler
  (-> routes
      (ring.middleware.json/wrap-json-body {:keywords? true})
      ring.middleware.json/wrap-json-response
      (ring.middleware.defaults/wrap-defaults ring.middleware.defaults/api-defaults)
      routing/wrap-exception))

(defn initialize [http-config]
  (http-server/start http-config routes))
