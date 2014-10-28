; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.web
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    ))


(defonce conf (atom nil))

(defn top-handler [request]
  {:status 418})


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

(defn wrap-status-dispatch [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))


;#### the main handler ########################################################


(defn build-main-handler [context]
  ( -> top-handler
       (wrap-status-dispatch)
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       (auth/wrap-authenticate-and-authorize-service)
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       (routing/wrap-prefix context)
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       (http-basic/wrap)
       (routing/wrap-debug-logging 'cider-ci.builder.web)
       ))

;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (let [http-conf (-> @conf :http_server)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'ring.middleware.resource)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
