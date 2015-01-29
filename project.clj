; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(defproject cider-ci_dispatcher "2.1.0"
  :description "Cider-CI Dispatcher"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [
                 [cider-ci/clj-auth "2.1.0"]
                 [cider-ci/clj-utils "2.5.0"]
                 [honeysql "0.4.3"]
                 [org.clojure/tools.nrepl "0.2.7"]
                 ]
  :source-paths ["src"]
  :profiles {
             :dev { :resource-paths ["resources_dev"] }
             :production { :resource-paths [ "/etc/cider-ci_dispatcher" ] }}
  :aot [cider-ci.dispatcher.main] 
  :main cider-ci.dispatcher.main 
  :repositories [["tmp" {:url "http://maven-repo-tmp.drtom.ch" :snapshots false}]]
  :jvm-opts ["-Xmx256m"]
  )
