; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.http-server
  (:require 
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [ring.adapter.jetty :as jetty]
    ) 
  (:import 
    )) 


(defonce _server (atom nil))

(defn stop []
  (when-let [server @_server]
    (logging/info stop)
    (.stop server)
    (reset! _server nil)))

(defn start [conf main-handler ]
  "Starts (or stops and then starts) the webserver"
  (let [server-conf (conj {:ssl? false
                           :join? false} 
                          (select-keys (:web conf) [:port :host]))]
    (stop)
    (logging/info "starting server " server-conf)
    (reset! _server (jetty/run-jetty main-handler server-conf))))

