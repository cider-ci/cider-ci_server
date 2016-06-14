(ns cider-ci.builder.retention-sweeper
  (:require
    [cider-ci.builder.retention-sweeper.jobs]
    [cider-ci.builder.retention-sweeper.tasks]
    [cider-ci.builder.retention-sweeper.trials]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn initialize []
  (cider-ci.builder.retention-sweeper.jobs/initialize)
  (cider-ci.builder.retention-sweeper.tasks/initialize)
  (cider-ci.builder.retention-sweeper.trials/initialize))
