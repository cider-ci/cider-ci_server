(ns cider-ci.repository.data
  )

(defn sort-data [data]
  (cond
    (map? data)  (->> data
                      (map (fn [[k v]] [k (sort-data v)]))
                      (sort-by (fn [[k _]] k))
                      (into {}))
    :else data))




