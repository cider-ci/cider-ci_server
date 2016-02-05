; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(defproject cider-ci_repository "3.0.0"
  :description "Cider-CI Repository"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "6.0.0"]
                 [cider-ci/clj-utils "7.0.0"]

                 [drtom/honeysql "1.3.0-beta.4"]
                 [logbug "4.0.0"]

                 [cheshire "5.5.0"]
                 [clj-http "2.0.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.apache.commons/commons-io "1.3.2"]
                 [org.clojure/core.incubator "0.1.3"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/tools.nrepl "0.2.12"]

                 ]
  :source-paths ["src"]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}}
  :aot [cider-ci.repository.main]
  :main cider-ci.repository.main
  :repl-options {:timeout  120000}
  )
