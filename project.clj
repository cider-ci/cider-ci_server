(defproject cider-ci_api "2.0.0"
  :description "Cider-CI API"
  :url "https://github.com/cider-ci/cider-ci_api"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.0.0"]
                 [cider-ci/clj-utils "2.0.0"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.jruby/jruby-complete "1.7.13"]
                 [sqlingvo "0.6.5"]
                 ]
  :source-paths [ "src" ]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci_api-v2" ] }}
  :aot [cider-ci.api.main] 
  :main cider-ci.api.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  )

;(cemerick.pomegranate.aether/register-wagon-factory!  "scp" #(let [c (resolve 'org.apache.maven.wagon.providers.ssh.external.ScpExternalWagon)] (clojure.lang.Reflector/invokeConstructor c (into-array []))))

