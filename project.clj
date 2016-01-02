; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci_dispatcher "3.0.0"
  :description "Cider-CI Dispatcher"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "5.0.1"]
                 [cider-ci/clj-utils "6.0.1"]

                 ;[logbug "2.0.0-beta.10"]
                 [drtom/honeysql "1.3.0-beta.4"]
                 [clojurewerkz/urly "1.0.0"]

                 [camel-snake-kebab "0.3.2"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/tools.nrepl "0.2.12"]


                 ; Explicit dependency fixes

                 ; urly depends to a too old guava dependency
                 [com.google.guava/guava "19.0"]


                 ]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}}
  :aot :all
  :main cider-ci.dispatcher.main
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :repl-options {:timeout  120000}
  )
