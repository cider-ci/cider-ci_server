(ns cider-ci.server.client.prod
  (:require [cider-ci.server.client.main]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(cider-ci.server.client.main/init!)
