(ns cider-ci.utils.self
  (:require
    [cider-ci.utils.system :as system]

    [clojure.java.io :as io]
    [clj-yaml.core :as yaml]
    [clj-time.core :as time]


    [logbug.debug :as debug]
    [logbug.catcher :refer [snatch]]
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))

(defn group-name []
  (-> "project-info.clj"
      io/resource
      slurp
      read-string
      :project-group))

(defn project-name []
  (-> "project-info.clj"
      io/resource
      slurp
      read-string
      :project-name))

(defn release []
  (snatch
    {:return-fn (fn [e]
                  {:version_major 4
                   :version_minor 0
                   :version_patch 0
                   :version_pre "TEST"
                   :version_build nil
                   :edition nil})}
    (-> "releases.yml"
        clojure.java.io/resource
        slurp
        yaml/parse-string
        :releases
        first )))

(defn version []
  (let [release (release)]
    (str (:version_major release)
         "." (:version_minor release)
         "." (:version_patch release)
         (when-let [pre (:version_pre release)]
           (str "-" pre))
         (when-let [build (:version_build release)]
           (str "+" build)))))

(defn current-time []
  (str (time/now)))

(defn deploy-info []
  (snatch
    {:return-fn (fn [e]
                  {:tree_id "TEST"
                   :commit_id "TEST"
                   :time (current-time) })}
    (-> "deploy-info.yml"
        clojure.java.io/resource
        slurp
        yaml/parse-string)))

(defn group-edition-str []
  (str (group-name)
       (when-let  [edition (-> release :edition)]
         (str "_" edition))
       ))

(defn application-str []
  (-> (str (group-edition-str)
           "/" (project-name)
           " " (version)
           "+" (-> (deploy-info) :tree_id))
      (clojure.string/replace #"\s+" " ")
      clojure.string/trim))

(defn cider-ci-str []
  (-> (str (group-edition-str)
           " "
           " " (version)
           "+" (-> (deploy-info) :tree_id))
      (clojure.string/replace #"\s+" " ")
      clojure.string/trim))
