; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(import 'java.io.File)
(load-file (str "src" File/separator "cider_ci" File/separator "repository.clj"))

(defproject cider-ci_repository cider-ci.repository/VERSION
  :description "Cider-CI Repository"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-utils "8.3.0"]

                 [drtom/honeysql "1.3.0-beta.4"]
                 [logbug "4.0.0"]

                 [cheshire "5.5.0"]
                 [clj-http "2.1.0"]
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
