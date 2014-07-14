; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.web
  (:require 
    [cider-ci.utils.http-server :as http-server]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.middleware.json]
    [ring.util.response]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom {}))

(defn attachments-dir-path []
  (:path (:attachments @conf)))

(defn put-attachment [request]
  (logging/debug put-attachment [request])
  (let [id (java.util.UUID/randomUUID)
        file (io/file (str (attachments-dir-path) "/" id))
        {content-type :content-type content-length :content-length} request
        {{trial-id :trial_id path :*} :route-params}  request ]
    (with-open [in (io/input-stream (:body request))
                out (io/output-stream file)]
      (clojure.java.io/copy in out))
    (jdbc/insert! (:ds @conf) 
                  :attachments
                  {:id id 
                   :path path
                   :content_length content-length
                   :content_type (or content-type "application/octet-stream")
                   :trial_id (java.util.UUID/fromString trial-id) })

    {:status 204})) 

(defn get-attachment [{{trial-id :trial_id path :*} :route-params}]
  (logging/debug get-attachment [trial-id path])
  (if-let [attachment (first (jdbc/query (:ds @conf)
                                         ["SELECT * FROM attachments 
                                          WHERE trial_id = ?::UUID 
                                          AND path = ?" trial-id path]))]
    (let [path (str (attachments-dir-path) "/" (:id attachment))]
      (-> (ring.util.response/file-response path)
          (ring.util.response/header "X-Sendfile" path)
          (ring.util.response/header "content-type" (:content_type attachment))))
    {:status 404}))

(defn log-handler [handler level]
  (fn [request]
    (logging/debug "log-handler " level " request: " request)
    (let [response (handler request)]
      (logging/debug  "log-handler " level " response: " response)
      response)))


(defn build-routes [context]
  (cpj/routes 
    (cpj/context context []
                 (cpj/GET "/attachments/trials/:trial_id/*" request
                          (get-attachment request)) 
                 (cpj/PUT "/attachments/trials/:trial_id/*" request
                          (put-attachment request)) 
                 )))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (log-handler 1)
       (ring.middleware.json/wrap-json-params)
       (log-handler 0)))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (.mkdirs (io/file (attachments-dir-path)))
  (http-server/start @conf (build-main-handler)))

