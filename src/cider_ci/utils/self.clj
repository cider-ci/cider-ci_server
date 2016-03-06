(ns cider-ci.utils.self
  (:require

    [logbug.debug :as debug]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))

(def application-str
  (-> (str cider-ci.self/GROUP "/" cider-ci.self/NAME
           " " cider-ci.self/EDITION
           " " cider-ci.self/VERSION)
      (clojure.string/replace #"\s+" " ")
      clojure.string/trim))

(def cider-ci-str
  (-> (str cider-ci.self/GROUP
           " " cider-ci.self/EDITION
           " " cider-ci.self/VERSION)
      (clojure.string/replace #"\s+" " ")
      clojure.string/trim))
