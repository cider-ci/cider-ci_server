; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.tm.executor
  )


(defn base-url [executor]
  (let [protocol (if (:ssl executor) "https" "http")]
    (str protocol "://" (:host executor) ":"  (:port executor))))

(defn ping-url [executor]
  (str (base-url executor) "/ping"))


