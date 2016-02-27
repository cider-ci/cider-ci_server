(ns cider-ci.repository.main
  (:gen-class)
  (:require
    [cider-ci.repository.repositories :as repositories]
    [cider-ci.repository.web :as web]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    [pg-types.all]
    ))

(defn -main [& args]
  (catcher/with-logging {}
    (logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
    (config/initialize {:overrides {:service :repository}})
    (rdbms/initialize (get-db-spec :dispatcher))
    (nrepl/initialize (-> (get-config) :services :repository :nrepl))
    (messaging/initialize (:messaging (get-config)))
    (repositories/initialize)
    (web/initialize)))
