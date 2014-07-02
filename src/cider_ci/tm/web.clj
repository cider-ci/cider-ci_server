; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.web
  (:require 
    [cider-ci.tm.trial :as trial]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom nil))
 

(defonce _update-trial (atom nil))
(defn update-trial [id params]
  (reset! _update-trial [id params])
  (logging/warn "TODO" update-trial [id params])
  (trial/update id (clojure.walk/keywordize-keys params))
  {:status 200}
  )
;(apply update-trial @_update-trial)


(defn build-routes []
  (cpj/routes 
    (cpj/context "/cider-ci/executors_api_v1" []

                 (cpj/PATCH "/trials/:id" 
                            {{id :id} :params json-params :json-params} 
                            (update-trial id json-params))

                 (cpj/GET "/" [] "OK")

                 )))


; /cider-ci/executors_api_v1/trials/85bcbb1e-3f15-415b-9d59-3d87ae7bdd62


(defn log-handler [handler]
  (fn [request]
    (logging/debug [log-handler request])
    (handler request)))

(def main-handler
  ( -> (cpj.handler/site (build-routes))
       (log-handler)
       (ring.middleware.json/wrap-json-params)
       (log-handler)
       ))

(defonce server nil)

(defn stop-server []
  (logging/info "stopping server")
  (. server stop)
  (def server nil))

(defn start-server []
  "Starts (or stops and then starts) the webserver"
  (let [server-conf (conj {:ssl? false
                           :join? false} @conf)]
    (if server (stop-server)) 
    (logging/info "starting server " server-conf)
    (def server (jetty/run-jetty main-handler server-conf))))

(defn initialize [new-conf]
  (reset! conf new-conf)
  (start-server))
