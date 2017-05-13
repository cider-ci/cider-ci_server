(ns cider-ci.ui2.prod
  (:require [cider-ci.client.main]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(cider-ci.client.main/init!)
