(ns cider-ci.server.ui2.prod
  (:require [cider-ci.server.client.main]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(cider-ci.server.client.main/init!)
