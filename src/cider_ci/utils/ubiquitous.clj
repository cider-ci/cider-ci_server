(ns cider-ci.utils.ubiquitous
  (:require
    [clojure.tools.logging :as logging]
    ))

(defn kw2str [k]
  (logging/warn (str "cider-ci.utils.ubiquitous/kw2str is deprecated."
                     " Use cider-ci.utils.core/to-cistr instead."))
  (if (keyword? k) (subs (str k) 1) (str k)))
