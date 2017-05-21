(ns cider-ci.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:gen-class)
  (:require
    [cider-ci.builder.main]
    [cider-ci.dispatcher.main]
    [cider-ci.repository.main]
    [cider-ci.storage.main]
    [cider-ci.server]

    [cider-ci.utils.app :as app]
    [cider-ci.web :as web]

    [logbug.catcher :as catcher]
    ))


(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    (app/init :server web/build-main-handler)
    (cider-ci.server/initialize)
    (cider-ci.repository.main/initialize)
    (cider-ci.builder.main/initialize)
    (cider-ci.dispatcher.main/initialize)
    (cider-ci.storage.main/initialize)
    ))
