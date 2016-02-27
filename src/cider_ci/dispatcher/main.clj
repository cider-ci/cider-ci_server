; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.main
  (:gen-class)
  (:require
    [cider-ci.dispatcher.abort :as abort]
    [cider-ci.dispatcher.dispatch :as dispatch]
    [cider-ci.dispatcher.dispatch.timeout-sweeper]
    [cider-ci.dispatcher.job :as job]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.web :as web]

    [cider-ci.utils.config :as config :refer [get-db-spec]]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.messaging :as messaging]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    ))

(defn -main [& args]
  (catcher/with-logging {}
    (logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
    (config/initialize {:overrides {:service :dispatcher}})
    (rdbms/initialize (get-db-spec :dispatcher))
    (let [conf (config/get-config)]
      (nrepl/initialize (-> conf :services :dispatcher :nrepl))
      (messaging/initialize (:messaging conf))
      (task/initialize)
      (job/initialize)
      (web/initialize)
      (dispatch/initialize)
      (abort/initialize)
      (cider-ci.dispatcher.dispatch.timeout-sweeper/initialize))))

