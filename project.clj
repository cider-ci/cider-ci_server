(defproject cider-ci_dispatcher "2.0.0"
  :description "Cider-CI Dispatcher"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.0.0"]
                 [cider-ci/clj-utils "2.0.0"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 ]
  :source-paths ["src"]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci_dispatcher" ] }}
  :aot [cider-ci.dispatcher.main] 
  :main cider-ci.dispatcher.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  )
