; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.ring
  (:require
    [clojure.walk :refer [keywordize-keys]]

    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    ))


(defn wrap-keywordize-request [handler]
  (fn [request] (-> request keywordize-keys handler)))
