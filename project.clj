; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(defproject cider-ci_repository "2.2.0"
  :description "Cider-CI Repository"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.2.2"]
                 [cider-ci/clj-utils "2.13.0-beta.2"]
                 [clj-jgit "0.8.3"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/tools.nrepl "0.2.8"]
                 ]
  :source-paths ["src"]
  :profiles {:dev 
             {:dependencies [[midje "1.6.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}} 
  :aot [cider-ci.repository.main] 
  :main cider-ci.repository.main 
  :jvm-opts ["-Xmx256m"]
  )
