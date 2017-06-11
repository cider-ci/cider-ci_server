(ns cider-ci.utils.duration-tests
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.duration :refer :all]
    ))

(deftest duration

  (is (instance? Double (parse-string-to-seconds "1 Second")))

  (is (thrown? Exception (parse-string-to-seconds "1 hourglass" )))

  (is (thrown? Exception (parse-string-to-seconds "Blah" )))

  (is (= 1.0 (parse-string-to-seconds "1 Second")))

  (is (= 30.0 (parse-string-to-seconds "0.5 Minutes")))

  (is (= 150.0 (parse-string-to-seconds "2.5 Minutes")))

  (is (= 3600.333 (parse-string-to-seconds "1 hour and 333 milliseconds")))

  (is (=
       (parse-string-to-seconds "1 Year and 3 Months and 3 weeks, 3 days , 7 minutes plus 1 second and 3 milliseconds")
       (+ (* 1 YEAR)
          (* 3 MONTH)
          (* 3 WEEK)
          (* 3 DAY)
          (* 7 MINUTE)
          (* 1 SECOND)
          (* 3 MILLISECOND))))

  (is (instance? org.joda.time.ReadablePeriod (period "100 Years")))

  )


