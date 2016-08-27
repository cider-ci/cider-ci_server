(ns cider-ci.utils.core)

(defn deep-merge [& vals]
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn presence [v]
  "Returns nil if v is a blank string. Returns v otherwise."
  (cond
    (string? v) (if (clojure.string/blank? v) nil v)
    :else v))

(defn to-cistr [v]
  "Converts v to a string without preceding colons and preserving `/` if v is a
  keyword.  Converts v to string by applying (str v) if v is not a keyword.
  Invariant: (= s (-> s keyword to-cistr)) if s is a string."
  (if (keyword? v)
    (subs (str v) 1)
    (str v)))

(defn to-ciset [value]
  "Converts a map of key/value pairs to a set of keys. A key is present in the
  set if and only if the value is truthy. Keys are also stringified by
  to-cistr. Inverse of to-cisetmap."
  (cond
    (map? value) (->> value
                   (filter (fn [[_ v]] v))
                   (map (fn [[k _]] k))
                   (map to-cistr)
                   set)
    (coll? value) (->> value
                    (map (fn [v] (to-cistr v)))
                    set)
    :else (throw (ex-info (str "I don't know how to convert a"
                               (type value) " to a ciset") {:input value}))))

(defn to-cisetmap [sq]
  "Converts a seq of keys into a map with true values.
  Keys are always converted to keywords. Inverse of to-cisetmap.
  Invariant:  (= (to-ciset v) (to-ciset (to-cisetmap (to-ciset v)))
  for to-ciset applicable types of v."
  (cond
    (map? sq) (->> sq
                   (map (fn [[k v]] [(keyword k) v ]))
                   (into {}))
    (coll? sq) (->> sq
                   (map (fn [k] [(keyword k) true]))
                   (into {}))
    :else (throw (ex-info (str "I don't know how to convert a"
                               (type sq) " to a cisetmap.") {:input sq}))))


