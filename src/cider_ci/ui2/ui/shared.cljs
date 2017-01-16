(ns cider-ci.ui2.shared
  (:require
    [goog.net.cookies]
    ))

(defn pre-component [data]
  [:pre (.stringify
          js/JSON
          (clj->js data) nil 2)])

(defn anti-forgery-token []
  (.get goog.net.cookies "cider-ci_anti-forgery-token"))
