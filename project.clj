; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/clj-utils "8.3.0"
  :description "Shared Clojure Utils for Cider-CI"
  :url "https://github.com/cider-ci/cider-ci_clj-utils"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [drtom/honeysql "1.3.0-beta.4"]
                 [logbug "4.0.0"]
                 [pg-types "2.1.2"]
                 [cider-ci/open-session "1.2.0"]

                 [cheshire "5.5.0"]
                 [clj-http "2.1.0"]
                 [clj-time "0.11.0"]
                 [clj-yaml "0.4.0"]
                 [com.github.mfornos/humanize-slim "1.2.2"]
                 [com.mchange/c3p0 "0.9.5"] ; Don not upgrade this. It depends on Java 1.8.
                 [com.novemberain/langohr "3.5.1"]
                 [compojure "1.4.0"]
                 [danlentz/clj-uuid "0.1.6"]
                 [joda-time "2.9.2"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [me.raynes/fs "1.4.6"]
                 [org.apache.commons/commons-lang3 "3.4"]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.slf4j/slf4j-log4j12 "1.7.18"]
                 [org.yaml/snakeyaml "1.17"]
                 [ring "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]

                 ; explicit transient deps to force conflict resolution
                 ;[org.clojure/java.classpath "0.2.3"]
                 ]

  :profiles {:dev {:dependencies [ ]
                   :plugins []
                   :resource-paths ["resources_dev"]
                   } }

  :java-source-paths ["java"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]

  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :plugins [[org.apache.maven.wagon/wagon-ssh-external "2.6"]]
  :deploy-repositories [ ["tmp" "scp://maven@schank.ch/tmp/maven-repo/"]]
  )

;(cemerick.pomegranate.aether/register-wagon-factory!  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)] (clojure.lang.Reflector/invokeConstructor c (into-array []))))
