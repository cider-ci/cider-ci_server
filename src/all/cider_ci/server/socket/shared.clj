(ns cider-ci.server.socket.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]]))

(defonce user-clients* (atom {}))

(declare chsk-send!)

(defn initialize-chsk-send! [send-fn]
  (def chsk-send! send-fn))
