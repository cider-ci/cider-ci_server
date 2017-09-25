; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.migrations :as migrations]
    [cider-ci.server.builder.main]
    [cider-ci.server.dispatcher.main]
    [cider-ci.server.repository.main]
    [cider-ci.server.storage.main]
    [cider-ci.server.executors]
    [cider-ci.server.web :as web]
    [cider-ci.server.state]
    [cider-ci.server.socket]
    [cider-ci.utils.app :as app]

    [clojure.pprint :refer [pprint]]
    [clojure.tools.cli :refer [parse-opts]]

    [logbug.catcher :as catcher]
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def common-cli-options
  [["-h" "--help"]
   ])

(defn main-usage [options-summary & more]
  (->> ["Cider-CI Server "
        ""
        "usage: cider-ci server [<opts>] SCOPE [<scope-opts>] [<args>]"
        ""
        "Scopes:"
        "run - start the server"
        "migrate - migrate the database"
        ""
        "Options:"
        options-summary
        ""
        ""

        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))



(defn run-main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn (fn [e] (System/exit -1))}
    (app/init :server web/build-main-handler)
    (cider-ci.server.repository.main/initialize)
    (cider-ci.server.builder.main/initialize)
    (cider-ci.server.dispatcher.main/initialize)
    (cider-ci.server.storage.main/initialize)
    (cider-ci.server.executors/initialize)
    (cider-ci.server.state/initialize)
    (cider-ci.server.socket/initialize)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args common-cli-options :in-order true)
        pass-on-args (->> [options (rest arguments)]
                          flatten (into []))]
    (cond
      (:help options) (println (main-usage summary {:args args :options options}))
      :else (case (-> arguments first keyword)
              :run (apply run-main pass-on-args)
              :migrate (apply migrations/main (rest arguments))
              (apply run-main pass-on-args)))))


