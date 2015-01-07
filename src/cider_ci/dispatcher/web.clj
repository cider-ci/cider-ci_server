; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.web
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.core]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.dispatcher.trial :as trial]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data :as data]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.json]
    ))


(defonce conf (atom nil))

;##### update trial ########################################################### 
 
(defn update-trial [id params]
  (trial/update (assoc (clojure.walk/keywordize-keys params)
                       :id id))
  {:status 200})

;##### status dispatch ######################################################## 

(defn status-handler [request]
  (let [stati {:rdbms (rdbms/check-connection)
               :messaging (messaging/check-connection)
               }]
    (if (every? identity (vals stati))
      {:status 200
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} }
      {:status 511
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} })))


;#### routing #################################################################


(defn build-routes [context]
  (cpj/routes 

    (cpj/GET "/status" request #'status-handler)

    (cpj/PATCH "/trials/:id" 
               {{id :id} :params json-params :json-params} 
               (update-trial id json-params))

    (cpj/GET "/" [] "OK")

    ))

(defn build-main-handler [context]
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (routing/wrap-debug-logging 'cider-ci.dispatcher.web)
       (ring.middleware.json/wrap-json-params)
       (routing/wrap-debug-logging 'cider-ci.dispatcher.web)
       (routing/wrap-prefix context)
       (routing/wrap-debug-logging 'cider-ci.dispatcher.web)
       (auth/wrap-authenticate-and-authorize-service)
       (routing/wrap-debug-logging 'cider-ci.dispatcher.web)
       (http-basic/wrap)
       (routing/wrap-debug-logging 'cider-ci.dispatcher.web)
       (routing/wrap-log-exception)
       ))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (cider-ci.auth.core/initialize 
    (conj {:ds (rdbms/get-ds)}
          (select-keys @conf [:basic_auth])))
  (let [http-conf (-> @conf :http_server)
        full-context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler full-context))))



;#### debug ###################################################################
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
