; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.nrepl
  (:require
    [cider-ci.utils.url.shared :refer [host-port-dissect path-dissect auth-dissect]]
    [ring.util.codec]
    [yaml.core :as yaml]
    [clojure.walk]
    ))


(defn nrepl-url? [url]
  (boolean (re-matches #"(?i)^nrepl:.+" url)))

(defn replace-str [s match replacement]
  (when s (clojure.string/replace s match replacement)))

(def pattern #"(?i)(nrepl)://([^/^\?^#]+)(/[^\?^#]*)?(\?[^#]+)?(#.*)?" )

(defn canonicalize-dissected [params]
  (->> params
       (map (fn [[k v]]
              (cond
                (and (= k :port) v) [k (Integer/parseInt v)]
                (and (= k :enabled) v) [k (yaml/parse-string v)]
                (= k :host) [:bind v]
                :else [k v])))
       (into {})))

(defn query-params [query-string]
  (if query-string
    (-> query-string
        ring.util.codec/form-decode
        clojure.walk/keywordize-keys)
    {}))

(defn dissect [url]
  (let [matches (re-matches pattern url)
        host-port (nth matches 2)
        query-string (-> matches (nth 4) (replace-str #"^\?" ""))]
    (-> {:protocol (-> matches (nth 1) clojure.string/lower-case)}
        (merge (host-port-dissect host-port))
        (merge (query-params query-string))
        canonicalize-dissected)))

;(dissect "nrepl://localhost:7881?enabled=yes")
;(dissect "nrepl://localhost:7881")
