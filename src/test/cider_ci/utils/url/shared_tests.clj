; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.url.shared-tests
  (:require
    [cider-ci.utils.url.shared :refer :all]
    [clojure.test :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(deftest test-host-port-dissect
  (testing "dissect regular host with port"
    (let [dissected (host-port-dissect "github.com:80")]
      (is (= (:host dissected) "github.com"))
      (is (= (:port dissected) "80"))))
  (testing "dissect regular host without port"
    (let [dissected (host-port-dissect "github.com")]
      (is (= (:host dissected) "github.com"))
      (is (= (:port dissected) nil))))
  (testing "dissect ipv6 host with port"
    (let [dissected (host-port-dissect "[1080::8:800:200C:417A]:81")]
      (is (= (:host dissected) "[1080::8:800:200C:417A]"))
      (is (= (:port dissected) "81")))))

(deftest test-auth-dissect
  (testing "auth with username and password"
    (let [dissected (auth-dissect "USER:PASS@")]
      (is (= (:username dissected) "USER"))
      (is (= (:password dissected) "PASS")))))

(deftest test-path-dissect
  (testing "nil input doesn't throw but delivers nil values"
    (is (= (-> nil path-dissect :project_namespace) nil))
    (is (= (-> nil path-dissect :project_name) nil))
    (is (= (-> nil path-dissect :context) nil)))
  (testing "standard github path schema"
    (let [path "/some-namespace/some-project.git"]
      (is (= (-> path path-dissect :project_name) "some-project"))
      (is (= (-> path path-dissect :project_namespace) "some-namespace"))
      (is (= (-> path path-dissect :context) ""))))
  (testing "gitlab schema with context"
    (let [path "/foo/bar/baz/some-namespace/some-project.git"]
      (is (= (-> path path-dissect :project_name) "some-project"))
      (is (= (-> path path-dissect :project_namespace) "some-namespace"))
      (is (= (-> path path-dissect :context) "/foo/bar/baz")))))



