; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.include-exclude
  (:refer-clojure :exclude [filter])
  )


(defprotocol Pattern
  (to-pattern [x]))

(extend-protocol Pattern

  java.lang.Boolean
  (to-pattern [x] (case x
                    true #"^.*$"
                    false #"$.+^"))

  java.lang.String
  (to-pattern [x]
    (cond (clojure.string/blank? x)
          (to-pattern false)
          :else (re-pattern x)))

  java.util.regex.Pattern
  (to-pattern [x] x)

  nil
  (to-pattern [x]
    (to-pattern false))
  )

(defn filter [include-match exclude-match coll]
  "Filter a collection of strings according to include-match and exclude-match.
   Non empty strings are converted to regex matchers and applied.
   A empty sting is equivalent to a false boolean value.
   `nil` is equivalent to a false boolean value.
   A true boolean include-match passes anything and a false nothing.
   A false boolean exclude-match passes anything and a true nothing."
  (->> coll
       (clojure.core/filter
         #(re-find (to-pattern include-match) (str %)))
       (clojure.core/filter
         #(not (re-find (to-pattern exclude-match) (str %))))))

