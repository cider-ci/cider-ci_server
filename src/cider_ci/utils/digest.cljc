(ns cider-ci.utils.digest
  (:require
    [cider-ci.utils.sha1 :refer [sha1]]

    #?(:cljs [goog.string :as gstring])
    #?(:cljs [goog.string.format :as format])

    #?(:clj [clojure.tools.logging :as logging])
    #?(:clj [clj-logging-config.log4j :as logging-config])
    #?(:clj [clojure.data.json :as json])

    ))

(def ^:private number-formatter "%.20f")

(defn- digest-number [n]
  #?(:cljs (gstring/format number-formatter n)
     :clj (format number-formatter (double n))))


(defn- digest-fallback [d]
  #?(:cljs (.stringify js/JSON (clj->js d))
     :clj (json/write-str d)))


(defn digest [d]
  ; #?(:clj (logging/debug (str "digesting: " d)) :cljs (js/console.log (clj->js (str "digesting: " d ))))
  (let [res (cond
              (nil? d) "0000000000000000000000000000000000000000"
              (string? d) (sha1 d)
              (keyword? d) (subs (str d) 1)
              (number? d) (digest-number d)
              (map? d) (if (empty? d)
                         (digest "{}")
                         (->> d
                              (sort-by (fn [[k _]] (str k)))
                              (map (fn [[k, v]] (str (digest k) " => " (digest v))))
                              digest))
              (seq? d) (if (empty? d)
                         (digest "[]")
                         (->> d (map digest) (clojure.string/join " ") digest))
              :else (digest (digest-fallback d))
              )]
    ;#?(:clj (logging/debug (str "digested " d " -> " res )) :cljs (js/console.log (clj->js (str "digested " d " -> " res))))
    res))
