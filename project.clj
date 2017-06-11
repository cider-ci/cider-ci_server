; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/server "0.0.0-PLACEHOLDER"
  :description "Cider-CI Server"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies ~(concat (read-string (slurp "project.dependencies.clj")))

  :resource-paths ["../config" "./config" "./resources"]

  :source-paths ["src/all"]
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
               {:source-paths ["src/all" "src/dev"]
                :compiler
                {:main "cider-ci.server.ui2.dev"
                 ;:asset-path "/cider-ci/ui2/js/out"
                 :asset-path "/cider-ci/js/out"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :output-dir "target/cljsbuild/public/js/out"
                 :source-map true
                 :optimizations :none
                 :pretty-print  true}}}}

  :minify-assets {:assets
                  {"resources/public/css/site.min.css"
                   "resources/public/css/site.css"}}

  :sass {:src "sass"
         :dst "resources/public/css"}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :nrepl-port 7002
             :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
             :css-dirs ["resources/public/css"]}

  :profiles {:dev
             {:dependencies [[ring/ring-mock "0.3.0"]
                             [ring/ring-devel "1.5.0"]
                             [prone "1.1.1"]
                             [figwheel-sidecar "0.5.4-5"]
                             [org.clojure/tools.nrepl "0.2.12"]
                             [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                             [pjstadig/humane-test-output "0.8.0"]]
              :plugins [[lein-figwheel "0.5.4-7"]
                        [lein-sassy "1.0.7"]]
              :source-paths ["src/all" "src/dev"]
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
