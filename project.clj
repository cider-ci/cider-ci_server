; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/clj-utils "3.0.1"
  :description "Shared Clojure Utils for Cider-CI"
  :url "https://github.com/cider-ci/cider-ci_clj-utils"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cheshire "5.5.0"]
                 [clj-http "1.1.2"]
                 [clj-time "0.10.0"]
                 [clj-yaml "0.4.0"]
                 [com.mchange/c3p0 "0.9.5"]
                 [com.novemberain/langohr "3.2.0"]
                 [compojure "1.4.0"]
                 [drtom/clj-uuid "0.0.8"]
                 [drtom/logbug "1.2.1"]
                 [joda-time "2.8.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [org.slf4j/slf4j-log4j12 "1.7.12"]
                 [org.yaml/snakeyaml "1.15"]
                 [pg-types "1.0.0"]
                 [ring "1.4.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.3.1"]

                 ; explicit transient deps to force conflict resolution
                 [org.clojure/java.classpath "0.2.2"]
                 ]

  :profiles {:dev {:dependencies [
                                  [midje "1.7.0"]
                                  [org.xerial/sqlite-jdbc "3.8.10.1"]
                                  ]
                   :plugins [[lein-midje "3.1.1"]]
                   :resource-paths ["resources_dev"]
                   } }

  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :plugins [[org.apache.maven.wagon/wagon-ssh-external "2.6"]]
  :deploy-repositories [ ["tmp" "scp://maven@schank.ch/tmp/maven-repo/"]]
  )

;(cemerick.pomegranate.aether/register-wagon-factory!  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)] (clojure.lang.Reflector/invokeConstructor c (into-array []))))
