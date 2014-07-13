(ns cider-ci.rm.main
  (:require 
    [cider-ci.rm.repositories :as repositories]
    [cider-ci.rm.submodules :as submodules]
    [cider-ci.rm.web :as web]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "/etc/repository-manager/conf.yml" 
          "conf.yml"]))

(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))

(defn -main [& args]
  (logging/debug [-main args]) 
  (read-config)
  (messaging/initialize (:messaging @conf))

  (nrepl/initialize (:nrepl @conf))

  (let [ds (rdbms/create-ds (get-db-spec))]
    (repositories/initialize (conj (select-keys @conf [:repositories])
                                   {:ds ds}))
    (submodules/initialize {:ds ds}))

  (web/initialize (select-keys @conf [:web :repositories]))

  
  )



