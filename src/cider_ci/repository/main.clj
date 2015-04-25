(ns cider-ci.repository.main
  (:require 
    [cider-ci.auth.core :as auth]
    [cider-ci.repository.repositories :as repositories]
    [cider-ci.repository.web :as web]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.thrown]
    [clojure.tools.logging :as logging]
    [pg-types.all]
    ))

(defn -main [& args]
  (catcher/wrap-with-log-error
    (drtom.logbug.thrown/reset-ns-filter-regex #".*cider-ci.*")
    (config/initialize)
    (rdbms/initialize (get-db-spec :dispatcher))
    (nrepl/initialize (-> (get-config) :services :repository :nrepl))
    (http/initialize (select-keys (get-config) [:basic_auth]))
    (messaging/initialize (:messaging (get-config)))
    (auth/initialize (select-keys (get-config) [:session :basic_auth :secret]))
    (repositories/initialize)
    (web/initialize (get-config))))

