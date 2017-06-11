; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.json
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [clojure.data.json]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [logbug.thrown :as thrown]
    ))

(clojure.core/extend-type org.joda.time.DateTime clojure.data.json/JSONWriter
  (-write [date-time out]
    (clojure.data.json/-write (time-format/unparse (time-format/formatters :date-time) date-time)
                              out)))

(clojure.core/extend-type org.joda.time.DateTime clojure.data.json/JSONWriter
  (-write [date-time out]
    (clojure.data.json/-write (time-format/unparse (time-format/formatters :date-time) date-time)
                              out)))

(clojure.core/extend-type clojure.lang.Atom clojure.data.json/JSONWriter
  (-write [obj out]
    (clojure.data.json/-write (deref obj) out)))


(clojure.core/extend-type java.util.concurrent.FutureTask clojure.data.json/JSONWriter
  (-write [future-task out]
    (clojure.data.json/-write {:done (. future-task isDone)}
                              out)))

(clojure.core/extend-type java.lang.Object clojure.data.json/JSONWriter
  (-write [obj out]
    (clojure.data.json/-write (str "Unspecified JSON conversion for: " (type obj)) out)))
