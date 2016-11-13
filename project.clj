; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(defproject cider-ci/repository "0.0.0-PLACEHOLDER"
  :description "Cider-CI Repository"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "http://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies ~(concat  (read-string (slurp "project.dependencies.clj"))
                          (read-string (slurp "../clj-utils/dependencies.clj")))

  :source-paths ["clj-utils/src" "src" ]

  :resource-paths ["../config" "./config" "./resources"]

  :plugins [[lein-environ "1.0.2"]
            [lein-cljsbuild "1.1.1"]
            [lein-asset-minifier "0.2.7"
             :exclusions [org.clojure/clojure]]]

  :cljsbuild {:builds
              {:min {:source-paths ["clj-utils/src" "src" "env/prod/cljs"]
                     :jar true
                     :compiler
                     {:output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/uberjar"
                      :optimizations :advanced
                      :pretty-print  false}}
               :app
               {:source-paths ["clj-utils/src" "src" "env/dev/cljs"]
                :compiler
                {:main "repository.dev"
                 :asset-path "/cider-ci/repositories/js/out"
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
              :source-paths ["env/dev/clj"]
              :resource-paths ["target/cljsbuild"]
              :injections [(require 'pjstadig.humane-test-output)
                           (pjstadig.humane-test-output/activate!)]
              :env {:dev true}}
             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj" "./clj-utils/src" "src"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :resource-paths ["target/cljsbuild"]
                       :aot [cider-ci.WebstackException #"cider-ci.*"]
                       :uberjar-name "repository.jar"
                       }}
  :main cider-ci.repository.main
  :repl-options {:timeout  120000}
  )
