; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/server "0.0.0-PLACEHOLDER"
  :description "Cider-CI Server"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies
  [
   [aleph "0.4.6"]
   [bidi "2.1.3"]
   [camel-snake-kebab "0.4.0"]
   [cheshire "5.8.0"]
   [cider-ci/open-session "2.0.0-beta.1"]
   [clj-http "3.9.0"]
   [cljs-http "0.1.45"]
   [cljs-http "0.1.44"]
   [cljsjs/jquery "3.2.1-0"]
   [cljsjs/moment "2.22.2-0"]
   [clojure-humanize "0.2.2"]
   [clojure-ini "0.0.2"]
   [com.cronutils/cron-utils "5.0.5"] ; upgrade to 6 or even more so 7 will brake things
   [com.github.mfornos/humanize-slim "1.2.2"]
   [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
   [com.taoensso/sente "1.12.0"]
   [commons-io/commons-io "2.6"]
   [compojure "1.6.1"]
   [drtom/clj-uuid "0.1.7"]
   [drtom/honeysql "2.0.0-ALPHA+1"]
   [environ "1.1.0"]
   [fipp "0.6.12"]
   [hiccup "1.0.5"]
   [hickory "0.7.1"]
   [hikari-cp "2.6.0"]
   [honeysql "0.9.3"]
   [io.forward/yaml "1.0.7"]
   [io.dropwizard.metrics/metrics-core "4.0.3"]
   [io.dropwizard.metrics/metrics-healthchecks "4.0.3"]
   [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
   [logbug "4.2.2"]
   [markdown-clj "1.0.2"]
   [me.raynes/fs "1.4.6"]
   [mvxcvi/clj-pgp "0.9.0"]
   [org.apache.commons/commons-io "1.3.2"]
   [org.apache.commons/commons-lang3 "3.7"]
   [org.bouncycastle/bcpg-jdk15on "1.59"]
   [org.bouncycastle/bcpkix-jdk15on "1.59"]
   [org.bouncycastle/bcprov-jdk15on "1.59"]
   [org.clojars.hozumi/clj-commons-exec "1.2.0"]
   [org.clojure/algo.generic "0.1.2"]
   [org.clojure/clojure "1.9.0"]
   [org.clojure/clojurescript "1.10.339" :scope "provided"]
   [org.clojure/core.incubator "0.1.4"]
   [org.clojure/core.memoize "0.7.1"]
   [org.clojure/data.json "0.2.6"]
   [org.clojure/java.jdbc "0.7.7"]
   [org.clojure/tools.cli "0.3.7"]
   [org.clojure/tools.logging "0.4.1"]
   [org.clojure/tools.nrepl "0.2.13"]
   [org.slf4j/slf4j-log4j12 "1.7.25"]
   [pg-types "2.4.0-PRE.1"]
   [prismatic/schema "1.1.7"]
   [reagent "0.8.1"]
   [reagent-utils "0.3.1"]
   [ring "1.6.3"]
   [ring-middleware-accept "2.0.3"]
   [ring-server "0.5.0"]
   [ring/ring-core "1.6.3"]
   [ring/ring-defaults "0.3.1"]
   [ring/ring-json "0.4.0"]
   [secretary "1.2.3"]
   [selmer "1.11.7"]
   [timothypratley/patchin "0.3.5"]
   [venantius/accountant "0.2.4"]

   [org.eclipse.jgit/org.eclipse.jgit "4.11.0.201803080745-r"]

   [viz-cljc "0.1.3"]
   [yogthos/config "1.1.1"]

   ; loom depdencies, for source are included via submodule,
   ; can be removed after changes have been submitted and accepted;
   ; this includes now also priority-map
   ;[aysylu/loom "1.0.0"]
   [org.clojure/data.priority-map "0.0.7"]
   ;[tailrecursion/cljs-priority-map "1.2.0"]

   ; explicit transient deps to force conflict resolution
   [com.google.guava/guava "23.0"]
   ;[org.clojure/tools.nrepl "0.2.13"]

   ]

  ; jdk 9 needs ["--add-modules" "java.xml.bind"]
  :jvm-opts #=(eval (if (re-matches #"^(9|10)\..*" (System/getProperty "java.version"))
                      ["--add-modules" "java.xml.bind"]
                      []))

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :source-paths ["src/all" "vendor/loom/src" "vendor/cljs-priority-map/src/cljs"]

  :resource-paths ["resources/all"]

  :test-paths ["src/test"]

  :aot [cider-ci.WebstackException cider-ci.ValidationException]

  :main cider-ci.main

  :plugins [[lein-asset-minifier "0.4.4" :exclusions [org.clojure/clojure]]
            [lein-cljsbuild "1.1.7"]
            [lein-environ "1.1.0"]
            [lein-shell "0.4.2"]] 

  :cljsbuild {:builds
              {:min {:source-paths ["src/all" "src/prod"]
                     :jar true
                     :compiler
                     {:output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/uberjar"
                      :optimizations :simple
                      :pretty-print  false}}
               :app
               {:source-paths ["src/all" "src/dev"]
                :compiler
                {:main "cider-ci.server.front.init"
                 :asset-path "/js/out"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :output-dir "target/cljsbuild/public/js/out"
                 :source-map true
                 :optimizations :none
                 :pretty-print  true}}}}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :nrepl-port 7002
             :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
             :css-dirs ["resources/public/css"]}

  :profiles {:dev
             {:dependencies [[com.cemerick/piggieback "0.2.2"]
                             [figwheel-sidecar "0.5.16"]
                             [org.clojure/tools.nrepl "0.2.13"]
                             [pjstadig/humane-test-output "0.8.3"]
                             [prone "1.6.0"]
                             [ring/ring-devel "1.6.3"]
                             [ring/ring-mock "0.3.2"]]
              :plugins [[lein-figwheel "0.5.16"]]
              :source-paths ["src/all" "src/dev"]
              :resource-paths ["resources/all" "resources/dev" "target/cljsbuild"]
              :injections [(require 'pjstadig.humane-test-output)
                           (pjstadig.humane-test-output/activate!)]
              :env {:dev true}}
             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["src/all" "src/prod"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :resource-paths ["target/cljsbuild"]
                       :aot [cider-ci.WebstackException cider-ci.ValidationException #"cider-ci.*"]
                       :uberjar-name "cider-ci.jar"}
             :test {:resource-paths ["resources/all" "resources/test" "target/cljsbuild"]}}

  :repl-options {:timeout  120000}
)
