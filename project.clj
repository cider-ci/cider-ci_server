(defproject cider-ci_storage "2.0.1"
  :description "Cider-CI Storage"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.0.1"]
                 [cider-ci/clj-utils "2.0.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 ]
  ;:pedantic? :warn
  :source-paths [ "src"]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci_storage" ] }}
  :aot [cider-ci.storage.main] 
  :main cider-ci.storage.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  )
