(defproject cider-ci_api "2.1.0"
  :description "Cider-CI API"
  :url "https://github.com/cider-ci/cider-ci_api"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cheshire "5.3.1"]
                 [cider-ci/clj-auth "2.1.0-rc.4"]
                 [cider-ci/clj-utils "2.0.1-rc.1"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.jruby/jruby-complete "1.7.13"]
                 [ring-middleware-accept "2.0.2"]
                 [ring/ring-core "1.3.1"]
                 [honeysql "0.4.3"]
                 ]
  :source-paths [ "json-roa/src" 
                  "src" ]
  :test-paths ["test"
               "json-roa/test"]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci_api-v2" ] }}
  :aot [cider-ci.api.main] 
  :main cider-ci.api.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  )

;(cemerick.pomegranate.aether/register-wagon-factory!  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)] (clojure.lang.Reflector/invokeConstructor c (into-array []))))

