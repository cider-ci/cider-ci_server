; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/builder "0.0.0-PLACEHOLDER"
  :description "Cider-CI Builder"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [drtom/honeysql "1.3.0-beta.4"]
                 [org.clojure/core.memoize "0.5.8"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 ]
  :plugins [[cider-ci/lein_cider-ci_dev "0.2.0"]]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :source-paths ["src" "../clj-utils/src"]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}}
  :aot [:all]
  :main cider-ci.builder.main
  :repl-options {:timeout  120000}
  )
