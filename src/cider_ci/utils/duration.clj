; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.utils.duration
  (:require
    [drtom.logbug.debug :as debug]
    [clojure.tools.logging :as logging]
    [duckling.core :as duckling]
    ))

(duckling/load! {:en$core {:corpus ["en.numbers"]
                           :rules ["en.numbers" "en.duration"]}})

(defn parse-string-to-seconds [duration]
  (or (-> duration
          (#(duckling/parse :en$core % [:duration]))
          first :value :normalized :value)
      (throw (ex-info "Duration parsing error."
                      {:duration-value duration}))))

;(parse-string-to-seconds "3 Minutes")

;(duckling/parse :en$core "900 Seconds" [:duration])
