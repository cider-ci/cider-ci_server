(ns cider-ci.repository.prod
  (:require [cider-ci.repository.ui.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
