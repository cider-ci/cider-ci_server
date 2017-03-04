; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.shared.cron-tests
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.shared.cron :as cron]

    [clj-time.core :as time]

    [clojure.test :refer :all]
    )
  (:import
    [cider_ci ValidationException]
    ))


(deftest parse!

  (testing "parsing a typically used cron expression passes"
    (is (cron/parse! "30 5 ? ? MON-FRI")))

  (testing "parsing a bogus cron expression throws"
    (is (thrown-with-msg?
          ValidationException #"BogusDay"
          (cron/parse! "* * * * BogusDay"))))

  (testing "parsing with set day-of-month throws"
    (is (thrown-with-msg?
          ValidationException #"day of the month"
          (cron/parse! "* * 28 * *")
          )))

  (testing "parsing with set month throws"
    (is (thrown-with-msg?
          ValidationException #"month"
          (cron/parse! "* * * 12 *")))))

(deftest fire?

  (testing "an always matching cron expression fires (with 1 Minute max-minutes-delay)"
    (is (= true (cron/fire? "* * * * *" 1))))

  (let [minute-minus-3 (time/minute (time/minus (time/now) (time/minutes 3)))]

    (testing "a cron-s matching three minutes before now should fire when max-minutes-delay is set to 5"
      (is (= true (cron/fire? (format "%d * * * *" minute-minus-3) 5))))

    (testing "a cron-s matching three minutes before now should NOT fire when max-minutes-delay is set to 1"
      (is (= false (cron/fire? (format "%d * * * *" minute-minus-3) 1))))))



