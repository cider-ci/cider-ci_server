; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/dispatcher "0.0.0-PLACEHOLDER"
  :description "Cider-CI Dispatcher"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [camel-snake-kebab "0.3.2"]
                 [drtom/honeysql "1.3.0-beta.4"]
                 [org.clojure/core.memoize "0.5.8"]
                 ]
  :plugins [[cider-ci/lein_cider-ci_dev "0.2.0"]]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :source-paths ["src" "../clj-utils/src"]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}}
  :aot [:all]
  :main cider-ci.dispatcher.main
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]
  :repl-options {:timeout  120000}
  )
