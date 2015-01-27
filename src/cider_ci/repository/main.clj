(ns cider-ci.repository.main
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.repository.repositories :as repositories]
    [cider-ci.repository.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "conf.yml"]))

(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))

(defn -main [& args]
  (logging/debug [-main args]) 
  (read-config)
  (nrepl/initialize (:nrepl @conf))
  (http/initialize (select-keys @conf [:basic_auth]))
  (messaging/initialize (:messaging @conf))
  (rdbms/initialize (get-db-spec))
  (auth/initialize (select-keys @conf [:session :basic_auth]))
  (repositories/initialize (select-keys @conf [:repositories]))
  (web/initialize (select-keys @conf [:http_server :repositories])))
