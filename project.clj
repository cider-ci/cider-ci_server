; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci_api "3.0.0"
  :description "Cider-CI API"
  :url "https://github.com/cider-ci/cider-ci_api"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cheshire "5.5.0"]
                 [cider-ci/clj-auth "5.0.1"]
                 [cider-ci/clj-utils "6.0.0"]
                 [cider-ci/open-session "1.2.0"]
                 [drtom/honeysql "1.3.0-beta.4"]
                 [logbug "3.0.0"]

                 [json-roa/clj-utils "1.0.0"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [ring-middleware-accept "2.0.3"]
                 [ring/ring-core "1.4.0"]

                 [org.clojure/java.classpath "0.2.3"]
                 [org.clojure/data.json "0.2.6"]
                 ]
  :source-paths ["src"]
  :test-paths ["test"]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]} }
  :resource-paths ["./config" "../config" "./resources"]
  :aot :all
  :main cider-ci.api.main
  :repl-options {:timeout  120000}
  )

;(cemerick.pomegranate.aether/register-wagon-factory!  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)] (clojure.lang.Reflector/invokeConstructor c (into-array []))))

