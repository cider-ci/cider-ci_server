; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.ring
  (:require
    [clojure.walk :refer [keywordize-keys]]
    [cider-ci.WebstackException]

    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    )

  (:import [cider_ci WebstackException])
  )


(defn wrap-keywordize-request [handler]
  (fn [request] (-> request keywordize-keys handler)))

(defn web-ex [s mp]
  (WebstackException. s mp))

(defn wrap-webstack-exception [handler]
  (fn [request]
    (catcher/snatch
      {:level :warn
       :return-fn #(if (instance? WebstackException %)
                     (ex-data %)
                     (throw %))}
      (handler request))))
