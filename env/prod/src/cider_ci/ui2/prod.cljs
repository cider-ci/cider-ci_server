(ns cider-ci.ui2.prod
  (:require [cider-ci.ui2.ui :as ui]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(ui/init!)
