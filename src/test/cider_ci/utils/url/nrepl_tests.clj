; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.nrepl-tests
  (:require
    [cider-ci.utils.url.nrepl :refer :all]
    [clojure.test :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))

(deftest test-dissect
  (testing "nrepl url"
    (let [dissected-url (dissect "nrepl://localhost:7881")]
      (is (= {:protocol "nrepl"
              :bind "localhost"
              :port 7881}
             dissected-url)))))

