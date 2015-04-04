; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(defproject cider-ci_dispatcher "2.2.0"
  :description "Cider-CI Dispatcher"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "3.0.0-beta.4"]
                 [cider-ci/clj-utils "3.0.0-beta.1"]
                 [drtom/honeysql "1.1.0"]
                 [org.clojure/tools.nrepl "0.2.7"]
                 ]

  ; :source-paths ["src" "../../clj-auth/src"]
  ; :java-source-paths ["../../clj-auth/lib/bcrypt-ruby/ext/jruby"] 

  :profiles {:dev 
             {:dependencies [[midje "1.6.3"]]
              :plugins [[lein-midje "3.1.1"]]
              :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
              :resource-paths ["../config" "./config" "./resources"]}
             :production
             {:resource-paths ["/etc/cider-ci" "../config" "./config" "./resources"]}} 
  :aot [cider-ci.dispatcher.main] 
  :main cider-ci.dispatcher.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :jvm-opts ["-Xmx256m"]
  )
