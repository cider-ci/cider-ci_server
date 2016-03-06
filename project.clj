; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/clj-utils_v4 "0.0.0-PLACEHOLDER"
  :description "Shared Clojure Utils for Cider-CI"
  :url "https://github.com/cider-ci/cider-ci_clj-utils"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [ ]

  ;:plugins [[cider-ci/lein_cider-ci_dev "0.2.0"]]

  :profiles {:dev {:dependencies
                   ~(read-string (slurp "dependencies.clj"))
                   :plugins [
                             [org.apache.maven.wagon/wagon-ssh-external "2.6"]
                             ]
                   :resource-paths ["resources_dev"]
                   } }

  :java-source-paths ["java"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :deploy-repositories [ ["tmp" "scp://maven@schank.ch/tmp/maven-repo/"]]
  )

;(cemerick.pomegranate.aether/register-wagon-factory!  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)] (clojure.lang.Reflector/invokeConstructor c (into-array []))))
