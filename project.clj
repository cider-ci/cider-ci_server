(defproject cider-ci_storage-manger "0.1.0"
  :description "Cider-CI Storage-Manger"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [c3p0/c3p0 "0.9.1.2"]
                 [cider-ci/clj-auth "0.1.4"]
                 [clj-http "1.0.0"]
                 [clj-http "1.0.0"]
                 [clj-logging-config "1.9.12"]
                 [clj-time "0.8.0"]
                 [clj-yaml "0.4.0"]
                 [com.novemberain/langohr "2.11.0"]
                 [compojure "1.1.8"]
                 [joda-time "2.4"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [me.raynes/fs "1.4.6"]
                 [org.clojars.hozumi/clj-commons-exec "1.1.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]
                 [ring "1.3.0"] 
                 [ring-basic-authentication "1.0.5"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [robert/hooke "1.3.0"]

                 ; make some transient deps explicit and force conflict resolution
                 [com.fasterxml.jackson.core/jackson-core "2.3.2"]
                 [ring/ring-core "1.3.0"]
                 ]
  ;:pedantic? :warn
  :source-paths ["clj-utils/src"
                 "src"]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci/storage-manager" ] }}
  :aot [cider-ci.sm.main] 
  :main cider-ci.sm.main 
  )
