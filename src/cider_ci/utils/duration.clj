; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.utils.duration
  (:require
    [logbug.debug :as debug]
    [clojure.tools.logging :as logging]
    ))

(def FILL-REXS
  [#"(?i)and"
   #"(?i)plus"
   #","
   ])

(defn drop-fillwords [sq]
  (->> sq
       (filter (fn [word]
                 (not (some (fn [re]
                              (re-matches re word)) FILL-REXS))))))

(defn split-by-whitespaces [s]
  (clojure.string/split s #"\s+"))

(def MILLISECOND 0.001)
(def SECOND 1)
(def MINUTE (* 60 SECOND))
(def HOUR (* 60 MINUTE))
(def DAY (* 24 HOUR))
(def WEEK (* 7 DAY))
(def MONTH (* 30.436875 DAY))
(def YEAR (* 365.25 DAY))

(defn duration-type-into-secs-factor [dt]
  (cond
    (re-matches #"(?i)year(s*)(,*)" dt) YEAR
    (re-matches #"(?i)month(s*)(,*)" dt) MONTH
    (re-matches #"(?i)week(s*)(,*)" dt) WEEK
    (re-matches #"(?i)day(s*)(,*)" dt) DAY
    (re-matches #"(?i)hour(s*)(,*)" dt) HOUR
    (re-matches #"(?i)minute(s*)(,*)" dt) MINUTE
    (re-matches #"(?i)second(s*)(,*)" dt) SECOND
    (re-matches #"(?i)millisecond(s*)(,*)" dt) MILLISECOND
    :else (throw (ex-info (str "The duration " dt " could not be interpreted!") {}))))

(defn convert-to-seconds-factors [sq]
  (->> sq
       (partition 2 2 "NO-TYPE-GIVEN")
       (map (fn [[d t]]
              [(* (Double/parseDouble d) (duration-type-into-secs-factor t))]))
       flatten ))

(defn parse-string-to-seconds [duration]
  (->> duration
       split-by-whitespaces
       drop-fillwords
       convert-to-seconds-factors
       (reduce +)))

;(parse-string-to-seconds "three days")
;(parse-string-to-seconds "3 elephants")
;(parse-string-to-seconds "3 milliseconds")
;(parse-string-to-seconds "1 Year")
;(parse-string-to-seconds "2 weeks")
;(parse-string-to-seconds "3 Minutes")
