(ns cider-ci.ValidationException
  (:import
    [clojure.lang ExceptionInfo IPersistentMap]
    )
  (:gen-class :init constructor
              :extends clojure.lang.ExceptionInfo
              :constructors
              {[String clojure.lang.IPersistentMap][String clojure.lang.IPersistentMap]}
              ))

(defn -constructor
  ([^String s, ^IPersistentMap data]
   [[ (str "Validation Error"
           (when-not (clojure.string/blank? s)
             (str " - " s)))
     data]]
   ))

