(defproject cider-ci_storage-manger "0.1.0"
  :description "Cider-CI Storage-Manger"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [c3p0/c3p0 "0.9.1.2"]
                 [clj-http "0.9.1"]
                 [clj-logging-config "1.9.10"]
                 [clj-time "0.7.0"]
                 [clj-yaml "0.3.1"]
                 [com.novemberain/langohr "2.11.0"]
                 [compojure "1.1.8"]
                 [joda-time "2.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.clojars.hozumi/clj-commons-exec "1.0.6"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.2"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [ring "1.3.0"] 
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [robert/hooke "1.3.0"]
                 ]
  :source-paths ["clj-utils/src"
                 "src"]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci/storage-manager" ] }}
  :aot [cider-ci.sm.main] 
  :main cider-ci.sm.main 
  )
