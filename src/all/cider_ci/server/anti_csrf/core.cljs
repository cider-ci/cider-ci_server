(ns cider-ci.server.anti-csrf.core
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.constants :as constants]
    [goog.net.cookies]
    ))

(defn anti-csrf-token []
  (.get goog.net.cookies constants/ANTI_CRSF_TOKEN_COOKIE_NAME))

(defn hidden-form-group-token-component []
  [:div.form-group
   [:input
    {:name :csrf-token
     :type :hidden
     :value (anti-csrf-token)}]])
