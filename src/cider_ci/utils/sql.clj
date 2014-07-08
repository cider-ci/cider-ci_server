(ns cider-ci.utils.sql
  )

(defn placeholders [col] 
  (->> col 
       (map (fn [_] "?"))
       (clojure.string/join  ", ")))
  ;(placeholders (range 1 5))



