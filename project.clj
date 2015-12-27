; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci_storage "3.0.0"
  :description "Cider-CI Storage"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "4.0.1"]
                 [cider-ci/clj-utils "4.2.1"]

                 [logbug "2.0.0-beta.10"]
                 [drtom/honeysql "1.3.0-beta.4"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 ]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]} }
  :resource-paths ["./config" "../config" "./resources"]
  :aot [cider-ci.storage.main]
  :main cider-ci.storage.main
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :jvm-opts ["-Xmx128m"]
  :repl-options {:timeout  120000}
  )
