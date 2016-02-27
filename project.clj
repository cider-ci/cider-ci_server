; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(import 'java.io.File)
(load-file (str "src" File/separator "cider_ci" File/separator "storage.clj"))

(defproject cider-ci_storage cider-ci.storage/VERSION
  :description "Cider-CI Storage"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-utils "8.3.0"]

                 [logbug "4.0.0"]
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
  :repl-options {:timeout  120000}
  )
