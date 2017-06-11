; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.http-tests
  (:require
    [cider-ci.utils.url.http :refer :all]
    [clojure.test :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))

(deftest test-dissect

  (testing "url with authentication and unicode in path"
    (let [dissected-url (dissect "http://user:pass@foo.com:8080/unicode_(✪)_in_parens?q=s#a")]
      (is (= (:path dissected-url) "/unicode_(✪)_in_parens"))))

  (testing "downcasing the protocol"
    (let [dissected-url (dissect "HttpS://github.com/Me/some-project.git")]
      (is (= (:protocol dissected-url) "https"))))

  (testing "stadard github url"
    (let [dissected-url (dissect "https://github.com/Me/some-project.git")]
      (is (= (:path dissected-url) "/Me/some-project.git"))
      (is (= (:project_name dissected-url) "some-project"))))

  (testing "auth properties"
    (let [dissected-url (dissect "https://USER:PASS@github.com/Me/some-project.git")]
      (is (= (:username dissected-url) "USER"))
      (is (= (:password dissected-url) "PASS"))))

  (testing "ipv6 url with path query and fragment"
    (let [dissected-url (dissect "http://[1080::8:800:200C:417A]:81/foo?answer=42#anchor")]
      (is (= (:host dissected-url) "[1080::8:800:200C:417A]"))
      (is (= (:port dissected-url) "81"))
      (is (= (:path dissected-url) "/foo"))
      (is (= (:query dissected-url) "?answer=42"))
      (is (= (:fragment_with_hash dissected-url) "#anchor")))))
