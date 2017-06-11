(ns cider-ci.server.builder.retention-sweeper
  (:require
    [cider-ci.server.builder.retention-sweeper.jobs]
    [cider-ci.server.builder.retention-sweeper.tasks]
    [cider-ci.server.builder.retention-sweeper.trials]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn initialize []
  (cider-ci.server.builder.retention-sweeper.jobs/initialize)
  (cider-ci.server.builder.retention-sweeper.tasks/initialize)
  (cider-ci.server.builder.retention-sweeper.trials/initialize))
