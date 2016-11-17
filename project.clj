; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/builder "0.0.0-PLACEHOLDER"
  :description "Cider-CI Builder"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies ~(concat  (read-string (slurp "project.dependencies.clj"))
                         (read-string (slurp "../clj-utils/dependencies.clj")))

  :source-paths ["clj-utils/src" "src"]
  :java-source-paths ["java"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :profiles {:dev
             {:dependencies [[midje "1.8.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :uberjar { :uberjar-name "builder.jar" }}
  :aot [cider-ci.builder.ValidationException cider-ci.WebstackException #"cider-ci.*"]
  :main cider-ci.builder.main
  :repl-options {:timeout  120000}
  )
