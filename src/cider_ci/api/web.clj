(ns cider-ci.api.web
  (:require 
    [cider-ci.utils.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [cider-ci.utils.http-server :as http-server]
    )
  (:import 
    [org.jruby.embed InvokeFailedException ScriptingContainer]
    ))


(defonce conf (atom nil))

;##### authenticate ########################################################### 

(defn run-jruby [ruby-code]
  (.runScriptlet (ScriptingContainer.) ruby-code))

(defn read-cookie [cookie-value]
  (json/read-str 
    (run-jruby (str "require 'uri'; 
                    URI.unescape('" cookie-value "') ")
               :key-fn keyword)))

(defn authenticate-request [request]
  (logging/debug authenticate-request [request])
  ;(logging/debug "COOKIES" (-> request :cookies (clojure.walk/keywordize-keys) :cider-ci_api-session :value))
  (logging/debug "COOKIES: " (cookies/cookies-request request {:decoder identity}))
  )

(defn auth-handler [handler]
  (fn [request]
    (authenticate-request request) 
    (handler request)))


;##### routes and handlers #################################################### 

(defn log-handler [handler level]
  (fn [request]
    (logging/debug "log-handler " level " request: " request)
    (let [response (handler request)]
      (logging/debug  "log-handler " level " response: " response)
      response)))

(defn build-routes [context]
  (cpj/routes 
    (cpj/context context []

                 (cpj/GET "/" [] "OK")

                 )))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (log-handler 3)
       (ring.middleware.json/wrap-json-params)
       (log-handler 2)
       (auth-handler)
       ;(log-handler 1)
       ;(cookies/wrap-cookies {:decoder identity})
       (log-handler 0)
       ))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (http-server/start @conf (build-main-handler)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


