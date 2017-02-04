; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.map
  (:require
    [cider-ci.utils.core :refer [to-cistr]]

    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))

(defn deep-merge [& vals]
  (logging/warn (str "cider-ci.utils.map/deep-merge is deprecated."
                     " Use cider-ci.utils.core/deep-merge instead."))
  (if (every? map? vals)
    (apply merge-with deep-merge vals)
    (last vals)))

(defn convert-to-array
  "Converts a map of maps to an array of maps. The key becomes the value of the
  :key property by applying (to-cistr key) property. If there is no :name property
  it will be set in the same way."
  [map-or-array]
  (if (and (map? map-or-array)
           (every?  map? (map second map-or-array)))
    (map (fn [[k m]]
           (conj m
                 {:key (to-cistr k)}
                 (when-not (:name m)
                   {:name (to-cistr k)})))
         map-or-array)
    map-or-array))
