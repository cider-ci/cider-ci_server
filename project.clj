; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci_builder "3.0.0-rc.1"
  :description "Cider-CI Builder"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "3.0.0-rc.1"]
                 [cider-ci/clj-utils "3.0.0-rc.2"]
                 [drtom/honeysql "1.1.0"]
                 [drtom/logbug "1.1.0"]
                 [org.clojure/core.memoize "0.5.7"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 ]
  :profiles {:dev 
             {:dependencies [[midje "1.6.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}} 
  :aot [cider-ci.builder.main] 
  :main cider-ci.builder.main 
  :jvm-opts ["-Xmx128m"]
  ;:source-paths ["src" "./tmp/logbug/src"]
  )
