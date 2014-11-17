(defproject json-roa/ring "0.0.0-alpha.0"
  :description "Ring middleware for negotiating, building and writing JSON-ROA"
  :url "https://github.com/json-roa/json-roa_clj-ring"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [
                 [cheshire "5.3.1"]
                 [org.clojure/clojure "1.3.0"]
                 [ring-middleware-accept "2.0.2"]
                 [ring/ring-core "1.3.1"]
                 ]
  :plugins [[codox "0.8.0"]]
  :profiles
  {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
   :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}})
