(defproject cider-ci/clj-utils "2.0.0"
  :description "Shared Clojure Utils for Cider-CI"
  :url "https://github.com/cider-ci/cider-ci_clj-utils"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [clj-http "1.0.0"]
                 [clj-logging-config "1.9.12"]
                 [clj-postgresql "0.3.0-SNAPSHOT" :exclusions [org.slf4j/slf4j-simple]]
                 [clj-time "0.8.0"]
                 [clj-yaml "0.4.0"]
                 [com.mchange/c3p0 "0.9.5-pre8"]
                 [com.novemberain/langohr "3.0.0-rc3"]
                 [compojure "1.2.0"]
                 [joda-time "2.5"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.clojars.hozumi/clj-commons-exec "1.1.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.postgresql/postgresql "9.3-1102-jdbc4"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [ring "1.3.1"] 
                 [ring/ring-jetty-adapter "1.3.1"]
                 [ring/ring-json "0.3.1"]
                 [robert/hooke "1.3.0"]

                 ; explicit transient deps to force conflict resolution
                 [com.fasterxml.jackson.core/jackson-core "2.4.3"]
                 [com.google.guava/guava "18.0"]
                 [org.clojure/tools.reader "0.8.9"]
                 ]

  :profiles {:dev {:dependencies [
                                  [midje "1.6.3"]
                                  [org.xerial/sqlite-jdbc "3.8.6"]
                                  ]
                   :plugins [[lein-midje "3.1.1"]]
                   :resource-paths ["resources_dev"]
                   } }

  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :plugins [[org.apache.maven.wagon/wagon-ssh-external "2.6"]]
  :deploy-repositories [ ["tmp" "scp://maven@schank.ch/tmp/maven-repo/"]]
  )

(cemerick.pomegranate.aether/register-wagon-factory!
  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)]
           (clojure.lang.Reflector/invokeConstructor c (into-array []))))
