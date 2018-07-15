(ns cider-ci.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:gen-class)
  (:require
    [cider-ci.server.main :as server-main]
    [cider-ci.executor :as executor]

    [logbug.thrown]

    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]

    [logbug.catcher :as catcher]
    ))

(logbug.thrown/reset-ns-filter-regex #".*cider[-_]ci.*")

(def cli-options
  [["-v" "--version"]
   ["-h" "--help"]
   ["-r" "--release-info"]
   ])

(defn usage [options-summary & more]
  (->> ["Cider-CI"
        ""
        "usage: cider-ci [<opts>] SCOPE [<scope-opts>] [<args>]"
        ""
        "Options:"
        options-summary
        ""
        "Scopes:"
        "server"
        "executor"
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))


(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options :in-order true)]
    (cond
      (:help options) (println (usage summary {:options options}))
      (:version options) (println (cider-ci.utils.self/version))
      (:release-info options) (println "Cider-CI" (cider-ci.utils.self/version) "\n"
                                       (:description (cider-ci.utils.self/release)))
      :else (case (-> arguments first keyword)
              :server (apply server-main/-main (rest arguments))
              :executor (apply executor/-main (rest arguments))
              (println (usage summary {:options options}))))))


;(-main "executor" "-h" "-s")
