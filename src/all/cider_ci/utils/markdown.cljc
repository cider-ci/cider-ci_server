(ns cider-ci.utils.markdown
  (:require
    [markdown.core :as markdown-clj]
    #?(:clj [clojure.core.memoize])
    ))

(defn- md2html* [s]
  ( #?(
       :clj markdown-clj/md-to-html-string
       :cljs markdown-clj/md->html)
    s
    :reference-links? true
    :footnotes? true))

(def md2html
  #?(
     :clj (clojure.core.memoize/lru md2html*)
     :cljs md2html*))

