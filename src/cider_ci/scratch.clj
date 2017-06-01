; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.scratch
  (:import (com.google.common.io BaseEncoding)))

(def b32 (BaseEncoding/base32))

(.encode b32 (.getBytes "test"))

(defn secret [n]
  (->> n crypto.random/bytes
       (.encode b32)
       (map char)
       (apply str)))

(secret 20)

