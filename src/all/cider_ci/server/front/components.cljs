(ns cider-ci.server.front.components
  (:require
    [cljs.pprint :refer [pprint]]
    ))

(defn pre-component [data]
  [:pre (with-out-str (pprint data))])


