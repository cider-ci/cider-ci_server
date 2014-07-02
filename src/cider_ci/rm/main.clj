(ns cider-ci.rm.main
  (:require 
    [cider-ci.rm.repositories :as repositories]
    [cider-ci.rm.web :as web]
    [cider-ci.nrepl :as nrepl]
    [cider-ci.messaging.core :as messaging]
    [cider-ci.sql.core :as sql]
    [clojure.tools.logging :as logging]
    [drtom.config-loader :as config-loader]
    ))


(declare 
  read-config
  )

(defonce conf (atom {}))

(defn -main [& args]
  (logging/debug [-main args]) 
  (read-config)
  (nrepl/initialize (:nrepl @conf))
  (messaging/initialize (:messaging @conf))
  (sql/initialize (:sql @conf))
  (repositories/initialize (:repositories @conf))
  (web/initialize (select-keys @conf [:web :repositories]))
  )


(defn read-config []
  (config-loader/read-and-merge
    [conf]
    ["/etc/cider-ci/rm/conf" "conf"]))

