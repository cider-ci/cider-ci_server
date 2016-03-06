(ns cider-ci.utils.tests.duration
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.duration :refer :all]
    ))

(deftest duration
  (is (=
       (parse-string-to-seconds "1 Year and 3 Months and 3 weeks, 3 days , 7 minutes plus 1 second and 3 milliseconds")
       (+ (* 1 YEAR)
          (* 3 MONTH)
          (* 3 WEEK)
          (* 3 DAY)
          (* 7 MINUTE)
          (* 1 SECOND)
          (* 3 MILLISECOND)))))
