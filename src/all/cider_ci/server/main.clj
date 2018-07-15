; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.main
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.migrations :as migrations]
    [cider-ci.server.run :as run]

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


(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args common-cli-options :in-order true)
        pass-on-args (->> [options (rest arguments)]
                          flatten (into []))]
    (cond
      (:help options) (println (main-usage summary {:args args :options options}))
      :else (case (-> arguments first keyword)
              :run (apply run/-main (rest arguments))
              :migrate (apply migrations/-main (rest arguments))))))


