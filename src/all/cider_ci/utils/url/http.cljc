; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.http
  (:require
    [cider-ci.utils.url.shared :refer [host-port-dissect path-dissect auth-dissect]]
    #?(:clj [yaml.core :as yaml])
    ))

(def pattern
  #"(?i)(https?)://([^@]+@)?([^/^\?]+)(/[^\?^#]*)?(\?[^#]+)?(#.*)?"
  ; 1.protocol     2.auth 3.host-port 4.path  5.query 6.fragment
  )

; TODO drop silly props; add :query-params with map;
;        ... once all the tests are passing

(defn dissect-basic-http-url [url]
  (as-> url url
    (re-matches pattern url)
    {:protocol (-> url (nth 1) clojure.string/lower-case)
     :authentication_with_at (nth url 2)
     :host_port (nth url 3)
     :path (nth url 4)
     :query (nth url 5)
     :fragment_with_hash (nth url 6)
     :parts url
     :url (nth url 0)}))

(defn dissect [url]
  (as-> url url
    (dissect-basic-http-url url)
    (merge url (host-port-dissect (:host_port url)))
    (merge url (auth-dissect (:authentication_with_at url)))
    (merge url (path-dissect (:path url)))))


(defn parse-query [query-string]
  #?(:clj
      (->> (clojure.string/replace query-string #"^\?" "")
           ring.util.codec/form-decode
           clojure.walk/keywordize-keys
           (map (fn [[qk qv]] [qk (yaml/parse-string qv)]))
           (into {}))))

(defn parse-base-url [url]
  #?(:clj (as-> url params
            (dissect params)
            (clojure.set/rename-keys params {:path :context})
            (select-keys params [:protocol :host :port :context :url :query])
            (->> params
                 (map (fn [[k v]]
                        (cond
                          (and (= k :port) (string? v)) [k (Integer/parseInt v)]
                          (and (= k :query)
                               (string? v)) [:query-params (parse-query v)]
                          :else [k v])))
                 (into {}))
            (merge (:query-params params) params)
            (dissoc params :query :query-params))
     :cljs (throw "Not yet implemented!")))

;(parse-base-url "http://localhost:1234/ctx?enabled=yes")
;(parse-base-url "http://localhost:1234")
;(parse-base-url "http://loclahost:8883?enabled=false")
