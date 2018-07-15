(ns cider-ci.utils.url.query-params
  (:refer-clojure :exclude [str keyword encode decode])
  (:require
    [cider-ci.utils.core :refer [keyword presence str]]

    [clojure.walk :refer [keywordize-keys]]
    [clojure.string :as string]
    [cider-ci.utils.url.shared :as shared]
    ))

(defn try-parse-json [x]
  #?(:cljs
      (try (-> x js/JSON.parse js->clj)
           (catch js/Object _ x))))


(defn to-json [x]
  #?(:cljs
      (.stringify js/JSON x)))

(defn decode-query-params [query-string]
  (->> (if-not (presence query-string) [] (string/split query-string #"&"))
       (reduce
         (fn [m part]
           (let [[k v] (string/split part #"=" 2)]
             (assoc m (-> k shared/decode keyword) (-> v shared/decode try-parse-json))))
         {})
       keywordize-keys))

(defn encode-query-params [params]
  (->> params
       (map (fn [[k v]]
              (str (-> k str shared/encode)
                   "="
                   (-> (if (coll? v) (to-json v) v)
                       str shared/encode))))
       (clojure.string/join "&")))
