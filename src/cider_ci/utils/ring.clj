; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.ring
  (:require
    [logbug.catcher :as catcher :refer [catch*]]
    [clojure.tools.logging :as logging]
    )
  (:import
    [cider_ci WebstackException]
    ))

(defn ex-web  [s mp]
  (WebstackException. s mp))

(defn wrap-webstack-exception [handler]
  (fn [request]
    (catch* :warn #(if (instance? WebstackException %)
                     (ex-data %)
                     (throw %)))
    (handler request)))


