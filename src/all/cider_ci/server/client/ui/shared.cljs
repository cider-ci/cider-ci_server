(ns cider-ci.server.client.shared
  (:require
    [fipp.edn :refer [pprint] :rename {pprint fipp}]
    [cljs.pprint :refer [pprint]]
    [goog.net.cookies]
    ))

(defn pre-component-json [data]
  [:pre (.stringify
          js/JSON
          (clj->js data) nil 2)])

(defn pre-component-fipp [data]
  [:pre (with-out-str (fipp data))])

(defn pre-component-pprint [data]
  [:pre (with-out-str (pprint data))])

(def pre-component pre-component-pprint)

(defn anti-forgery-token []
  (.get goog.net.cookies "cider-ci_anti-forgery-token"))
