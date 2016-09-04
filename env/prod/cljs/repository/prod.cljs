(ns repository.prod
  (:require [cider-ci.repository.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
