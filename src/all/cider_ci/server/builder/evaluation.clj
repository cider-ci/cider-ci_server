(ns cider-ci.server.builder.evaluation
  (:require
    [cider-ci.server.builder.evaluation.jobs :as jobs]
    [cider-ci.server.builder.evaluation.tasks :as tasks]
    ))

(defn initialize []
  (tasks/initialize)
  (jobs/initialize))

