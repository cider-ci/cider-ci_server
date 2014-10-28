(defproject cider-ci_builder "1.0.0"
  :description "Cider-CI Builder"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.0.0"]
                 [cider-ci/clj-utils "2.0.0"]
                 [drtom/clj-uuid "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 ]
  :source-paths [ "src"]
  :profiles {
             :dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-midje "3.1.1"]]
                   :resource-paths ["resources_dev"] }
             :production {:resource-paths [ "/etc/cider-ci_builder" ] }}
  :aot [cider-ci.builder.main] 
  :main cider-ci.builder.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  )
