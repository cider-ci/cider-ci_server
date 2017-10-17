; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/server "0.0.0-PLACEHOLDER"
  :description "Cider-CI Server"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies


[

 [aleph "0.4.3"]
 [camel-snake-kebab "0.4.0"]
 [cheshire "5.7.1"]
 [cider-ci/open-session "1.3.0"]
 [clj-http "3.6.1"]
 [clj-time "0.14.0"]
 [cljs-http "0.1.43"]
 [cljsjs/bootstrap "3.3.6-1"]
 [cljsjs/jquery "2.2.4-0"]
 [cljsjs/moment "2.17.1-1"]
 [clojure-humanize "0.2.2"]
 [clojure-ini "0.0.2"]
 ; TODO upgrade com.cronutils/cron-utils >= "6.0.0"
 [com.cronutils/cron-utils "5.0.5"]
 [com.github.mfornos/humanize-slim "1.2.2"]
 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
 [com.mchange/c3p0 "0.9.5.2"]
 [com.taoensso/sente "1.11.0"]
 [compojure "1.6.0"]
 [drtom/clj-uuid "0.1.7"]
 [drtom/honeysql "2.0.0-ALPHA+1"]
 [environ "1.1.0"]
 [fipp "0.6.9"]
 [hiccup "1.0.5"]
 [hickory "0.7.1"]
 [io.forward/yaml "1.0.6"]
 [joda-time "2.9.9"]
 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
 [logbug "4.2.2"]
 [markdown-clj "0.9.99"]
 [me.raynes/fs "1.4.6"]
 [org.apache.commons/commons-io "1.3.2"]
 [org.apache.commons/commons-lang3 "3.6"]
 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
 [org.clojure/algo.generic "0.1.2"]
 [org.clojure/clojure "1.8.0"]
 [org.clojure/clojurescript "1.9.854" :scope "provided"]  ; see guava below; also check `lein tree` and sync
 [org.clojure/core.incubator "0.1.4"]
 [org.clojure/core.memoize "0.5.9"]
 [org.clojure/data.json "0.2.6"]
 [org.clojure/java.jdbc "0.7.0"]
 [org.clojure/tools.cli "0.3.5"]
 [org.clojure/tools.nrepl "0.2.13"]
 [org.slf4j/slf4j-log4j12 "1.7.25"]
 [pg-types "2.3.0"]
 [prismatic/schema "1.1.6"]
 [reagent "0.7.0"]
 [reagent-utils "0.2.1"]
 [ring "1.6.2"]
 [ring-middleware-accept "2.0.3"]
 [ring-server "0.4.0"]
 [ring/ring-core "1.6.2"]
 [ring/ring-defaults "0.3.1"]
 [ring/ring-json "0.4.0"]
 [secretary "1.2.3"]
 [selmer "1.11.0"]
 [timothypratley/patchin "0.3.5"]

 ; included as a submodule for now
 ; can be removed after changes have been submitted and accepted;
 ;[venantius/accountant "0.2.0" :exclusions [org.clojure/tools.reader]]

 [viz-cljc "0.1.2"]
 [yogthos/config "0.8"]

 ; loom depdencies, for source are included via submodule,
 ; can be removed after changes have been submitted and accepted;
 ; this includes now also priority-map
 ;[aysylu/loom "1.0.0"]
 [org.clojure/data.priority-map "0.0.7"]
 ;[tailrecursion/cljs-priority-map "1.2.0"]

 ; explicit transient deps to force conflict resolution
 [com.google.guava/guava "23.0"]
 [org.clojure/tools.nrepl "0.2.13"]

 ]


  :resource-paths ["../config" "./config" "./resources"]

  :source-paths ["src/all" "vendor/loom/src" "vendor/accountant/src" "vendor/cljs-priority-map/src/cljs"]
  :test-paths ["src/test"]

  :plugins [[lein-environ "1.0.2"]
            [lein-cljsbuild "1.1.1"]
            [lein-asset-minifier "0.2.7"
             :exclusions [org.clojure/clojure]]]

  :cljsbuild {:builds
              {:min {:source-paths ["src/all" "src/prod"]
                     :jar true
                     :compiler
                     {:output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/uberjar"
                      :optimizations :advanced
                      :pretty-print  false}}
               :app
               {:source-paths ["src/all" "src/dev" "vendor/accountant/src"]
                :compiler
                {:main "cider-ci.server.client.dev"
                 ;:asset-path "/cider-ci/client/js/out"
                 :asset-path "/cider-ci/js/out"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :output-dir "target/cljsbuild/public/js/out"
                 :source-map true
                 :optimizations :none
                 :pretty-print  true}}}}

  :minify-assets {:assets
                  {"resources/public/css/site.min.css"
                   "resources/public/css/site.css"}}

  :sass {:src "resources/public/css"
         :dst "resources/public/css"}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :nrepl-port 7002
             :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
             :css-dirs ["resources/public/css"]}

  :profiles {:dev
             {:dependencies [[ring/ring-mock "0.3.1"]
                             [ring/ring-devel "1.6.2"]
                             [prone "1.1.4"]
                             [figwheel-sidecar "0.5.12"]
                             [org.clojure/tools.nrepl "0.2.13"]
                             [com.cemerick/piggieback "0.2.2"]
                             [pjstadig/humane-test-output "0.8.2"]]
              :plugins [[lein-figwheel "0.5.12"]
                        [lein-sassy "1.0.7"]]
              :source-paths ["src/all" "src/dev" "vendor/accountant/src"]
              :resource-paths ["target/cljsbuild"]
              :injections [(require 'pjstadig.humane-test-output)
                           (pjstadig.humane-test-output/activate!)]
              :env {:dev true}}
             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["src/all" "src/prod"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :resource-paths ["target/cljsbuild"]
                       :aot [cider-ci.WebstackException cider-ci.ValidationException #"cider-ci.*"]
                       :uberjar-name "cider-ci.jar"
                       }
             :test {:resource-paths ["resources_test"]
                    }}
  :aot [cider-ci.WebstackException cider-ci.ValidationException]
  :main cider-ci.main
  :repl-options {:timeout  120000}
  )
