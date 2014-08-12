(ns cider-ci.api.main
  (:require 
    [cider-ci.api.web :as web]
    [cider-ci.api.basic-auth :as basic-auth]
    [cider-ci.api.session-auth :as session-auth]
    [cider-ci.api.resources :as resources]
    [cider-ci.utils.config-loader :as config-loader]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.tools.logging :as logging]
    )
  (:import 
    [org.jruby.embed InvokeFailedException ScriptingContainer]
    ))


(defonce conf (atom {}))

(defn read-config []
  (config-loader/read-and-merge
    conf ["conf_default.yml" 
          "/etc/cider-ci_api/conf.yml" 
          "conf.yml"]))


(defn get-db-spec []
  (-> @conf (:database) (:db_spec) ))


(defn -main
  [& args]
  (read-config)
  (let [ds (rdbms/create-ds (get-db-spec))]
    (web/initialize (select-keys @conf [:web :basic_auth]))
    (session-auth/initialize {:ds ds}) 
    (basic-auth/initialize {:ds ds}) 
    (resources/initialize (assoc (select-keys @conf [:web :storage_manager_server]) :ds ds) )
    ))



;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


