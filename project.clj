; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(defproject cider-ci_repository "3.0.0"
  :description "Cider-CI Repository"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "3.0.0"]
                 [cider-ci/clj-utils "3.0.4"]
                 [honeysql "0.6.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/core.memoize "0.5.7"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 ]
  :source-paths ["src"]
  :profiles {:dev
             {:dependencies [[midje "1.7.0"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}}
  :aot [cider-ci.repository.main]
  :main cider-ci.repository.main
  :jvm-opts ["-Xmx256m"]
  :repl-options {:timeout  120000}
  )
