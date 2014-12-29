(defproject cider-ci/clj-utils "2.3.0-rc.2"
  :description "Shared Clojure Utils for Cider-CI"
  :url "https://github.com/cider-ci/cider-ci_clj-utils"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [robert/hooke "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-core "1.3.2"] 
                 [ring "1.3.2"] 
                 [pg-types "1.0.0-beta.6"]
                 [org.slf4j/slf4j-log4j12 "1.7.9"]
                 [org.postgresql/postgresql "9.3-1102-jdbc4"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojars.hozumi/clj-commons-exec "1.1.0"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [joda-time "2.6"]
                 [compojure "1.3.1"]
                 [com.novemberain/langohr "3.0.1"]
                 [com.mchange/c3p0 "0.9.5"]
                 [clj-yaml "0.4.0"]
                 [clj-time "0.9.0"]
                 [clj-logging-config "1.9.12"]
                 [clj-http "1.0.1"]
                 [cheshire "5.4.0"]

                 ; explicit transient deps to force conflict resolution
                 [org.clojure/tools.reader "0.8.13"]
                 ]

  :profiles {:dev {:dependencies [
                                  [midje "1.6.3"]
                                  [org.xerial/sqlite-jdbc "3.8.7"]
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
