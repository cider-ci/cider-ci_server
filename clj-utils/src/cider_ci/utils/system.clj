(ns cider-ci.utils.system
  (:require 
    [clj-commons-exec :as commons-exec]
    [clojure.tools.logging :as logging]
    ))


(defn exec [& args]
  (logging/debug exec [args])
  (let [res @(apply commons-exec/sh args)]
    (if (not= 0 (:exit res))
      (throw (IllegalStateException. (str "Unsuccessful shell execution" 
                                          args
                                          (:err res)
                                          (:out res)))) 
      res)))

