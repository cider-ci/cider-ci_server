; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.web
  (:require 
    [cider-ci.tm.trial :as trial]
    [cider-ci.utils.http-server :as http-server]
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


;##### routes and handlers #################################################### 

(defn log-handler [handler level]
  (fn [request]
    (logging/debug "log-handler " level " request: " request)
    (let [response (handler request)]
      (logging/debug  "log-handler " level " response: " response)
      response)))


(defn build-routes [context]
  (cpj/routes 
    (cpj/context (str context "/executors_api_v1") []

                 (cpj/PATCH "/trials/:id" 
                            {{id :id} :params json-params :json-params} 
                            (update-trial id json-params))

                 (cpj/GET "/" [] "OK")

                 )))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (log-handler 1)
       (ring.middleware.json/wrap-json-params)
       (log-handler 0)))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (http-server/start @conf (build-main-handler))
  )



