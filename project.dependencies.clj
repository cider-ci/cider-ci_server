[

 [aleph "0.4.1"]
 [camel-snake-kebab "0.4.0"]
 [cheshire "5.5.0"]
 [cheshire "5.6.3"]
 [cider-ci/open-session "1.3.0"]
 [clj-http "3.1.0"]
 [clj-time "0.12.0"]
 [clj-yaml "0.4.0"]
 [cljs-http "0.1.41"]
 [cljsjs/bootstrap "3.3.6-1"]
 [cljsjs/jquery "2.2.4-0"]
 [cljsjs/moment "2.10.6-4"]
 [clojure-humanize "0.2.0"]
 [com.cronutils/cron-utils "5.0.5"]
 [com.github.mfornos/humanize-slim "1.2.2"]
 [com.mchange/c3p0 "0.9.5.2"]
 [com.taoensso/sente "1.10.0"]
 [compojure "1.5.1"]
 [drtom/clj-uuid  "0.1.7"]
 [drtom/honeysql "2.0.0-ALPHA+1"]
 [environ "1.1.0"]
 [fipp "0.6.8"]
 [hiccup "1.0.5"]
 [joda-time "2.9.4"]
 [json-roa/clj-utils "1.0.0"]
 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
 [logbug "4.0.0"]
 [logbug "4.2.2"]
 [markdown-clj "0.9.91"]
 [me.raynes/fs "1.4.6"]
 [org.apache.commons/commons-io "1.3.2"]
 [org.apache.commons/commons-lang3 "3.4"]
 [org.clojars.hozumi/clj-commons-exec "1.2.0"]
 [org.clojure/clojure "1.8.0"]
 [org.clojure/clojurescript "1.9.93" :scope "provided"]  ; see guava below; also check `lein tree` and sync
 [org.clojure/core.incubator "0.1.3"]
 [org.clojure/core.memoize "0.5.8"]
 [org.clojure/data.json "0.2.6"]
 [org.clojure/java.jdbc "0.6.1"]
 [org.clojure/tools.nrepl "0.2.12"]
 [org.slf4j/slf4j-log4j12 "1.7.21"]
 [org.yaml/snakeyaml "1.17"]
 [pg-types "2.2.0"]
 [prismatic/schema "1.1.3"]
 [reagent "0.6.0-rc"]
 [reagent-utils "0.1.9"]
 [ring "1.5.0"]
 [ring-middleware-accept "2.0.3"]
 [ring-server "0.4.0"]
 [ring/ring-core "1.5.0"]
 [ring/ring-defaults "0.2.1"]
 [ring/ring-json "0.4.0"]
 [secretary "1.2.3"]
 [timothypratley/patchin "0.3.5"]
 [venantius/accountant "0.1.7" :exclusions [org.clojure/tools.reader]]
 [yogthos/config "0.8"]

 ; explicit transient deps to force conflict resolution
 [com.google.guava/guava "19.0"]
 [org.clojure/tools.nrepl "0.2.12"]

 ]
