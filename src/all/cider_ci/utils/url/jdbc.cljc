; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.jdbc
  (:require
    [cider-ci.utils.url.shared :refer [host-port-dissect path-dissect auth-dissect parse-int]]
    #?(:clj [ring.util.codec])
    [clojure.walk]
    ))


(defn jdbc-url? [url]
  (boolean (re-matches #"(?i)^jdbc:.+" url)))


(defn replace-str [s match replacement]
  (when s (clojure.string/replace s match replacement)))

;(jdbc-url? "jdbc:postgresql://cider-ci:cider-ci@localhost:5432/cider-ci_v4")

(def pattern
  #"(?i)(jdbc):([^/]+)+//([^@]+?@)?([^/]+)([^\?|#]+)(\?[^#]+)?(#.*)?" )

(defn canonicalize-dissected [params]
  (->> params
       (map (fn [[k v]]
              (cond
                (and (= k :port) v) [k (parse-int v)]
                (= k :max-pool-size) [k (parse-int v)]
                :else [k v])))
       (into {})))

(defn query-params [query-string]
  #?(:clj
      (if query-string
        (-> query-string
            ring.util.codec/form-decode
            clojure.walk/keywordize-keys))
      :cljs (throw (ex-info "Not implemented" {}))))

(defn subname [params]
  (str "//" 
       (when-let [host (:host params)] host)
       (when-let [port (:port params)] (str ":" port))
       "/" (:database params)))

(defn dissect [url]
  (let [matches (re-matches pattern url)
        auth (nth matches 3)
        host-port (nth matches 4)
        query-string (-> matches (nth 6) (replace-str #"^\?" ""))]
    (-> {:protocol (-> matches (nth 1) clojure.string/lower-case)
         :sub-protocol (-> matches (nth 2) (clojure.string/replace #":$" ""))
         :database (-> matches (nth 5) (clojure.string/replace #"^/" ""))}
        (merge (auth-dissect auth))
        (merge (host-port-dissect host-port))
        (merge (query-params query-string))
        canonicalize-dissected)))


;(dissect "jdbc:postgresql://cider-ci:cider-ci@localhost:5432/cider-ci_v4?max-pool-size=50")
;(dissect "jdbc:postgresql://cider-ci:cider-ci@localhost:5432/cider-ci_v4")

