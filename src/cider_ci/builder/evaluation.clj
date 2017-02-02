(ns cider-ci.builder.evaluation
  (:require
    [cider-ci.builder.evaluation.jobs :as jobs]
    [cider-ci.builder.evaluation.tasks :as tasks]
    ))

(defn initialize []
  (tasks/initialize)
  (jobs/initialize))

