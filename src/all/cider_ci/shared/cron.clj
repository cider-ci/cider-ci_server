; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.shared.cron
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clojure.core.memoize :as memo]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging])
  (:import
    [cider_ci ValidationException]

    [com.cronutils.descriptor CronDescriptor]
    [com.cronutils.model.definition CronDefinitionBuilder]
    [com.cronutils.model.time ExecutionTime]
    [com.cronutils.parser CronParser]
    [java.time ZonedDateTime]
    ))


(def cron-def
  (.. (CronDefinitionBuilder/defineCron)
      withMinutes
      and withHours
      and withDayOfMonth supportsHash supportsL supportsW
      and withMonth
      and withDayOfWeek supportsHash supportsL supportsW
      and instance
      ))

(def ^:private parser (CronParser. cron-def))

(def ^:private descriptor (CronDescriptor/instance))

(def ^:private pattern #"(?i)(\S+)\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)")

(defn- post-validate! [cron-s]
  (let [matches (re-matches pattern cron-s)]
    (let [month (nth matches 4)]
      (when-not (or (= "*" month)
                    (= "?" month))
        (throw (ValidationException.
                 (str "Only weekly timers are supported. "
                      "The month value '" month
                      "' in the cron expression must be either '*' or '?'.")
                 {}))))
    (let [day-of-month (nth matches 3)]
      (when-not (or (= "*" day-of-month)
                    (= "?" day-of-month))
        (throw (ValidationException.
                 (str "The maximum cron duration is one week. "
                      "The day of the month value '" day-of-month
                      "' in the cron expression must be either '*' or '?'.")
                 {}))))))

(defn- parse!-unmemoized [cron-s]
  (try
    (let [cron (.parse parser cron-s)]
      (or (.validate cron)
          (throw (ValidationException. (str "validate failed for " cron-s)
                                       {:value cron-s})))
      (post-validate! cron-s)
      cron)
    (catch IllegalArgumentException ia
      (throw (ValidationException. (.getMessage ia)
                                   {:value cron-s :ex ia})))))

(def parse!
  (memo/lru parse!-unmemoized :lru/threshold 1000))

(defn- execution-time-unmemoized [cron-s]
  (-> cron-s parse! ExecutionTime/forCron))

(def execution-time
  (memo/lru execution-time-unmemoized :lru/threshold 1000))

(defn describe [cron-s]
  (->> cron-s parse! (.describe descriptor)))

(defn fire? [cron-s max-minutes-delay]
  "Returns true if and only if the cron expression `cron-s` falls in the
  interval from `(now - max-minutes-delay)` to `now`. Throws a
  ValidationException if cron-s can not be parsed. Throws a ValidationException
  when month or day-of-month are set in cron-s."
  (<= (-> cron-s
          execution-time
          (.timeFromLastExecution (ZonedDateTime/now))
          .toMinutes)
      max-minutes-delay))

;(fire? "38 * * * *" 1)
;(parse!-unmemoized "* * * * *")
;(.parse! parser "* * * * *")
;(describe "30 5 ? ? MON-FRI")
;(describe "30 5 21 ? MON-FRI")
;(describe "30 5 ? 11 MON-FRI")
;(describe "30 5 ? ? MON-FRY")
