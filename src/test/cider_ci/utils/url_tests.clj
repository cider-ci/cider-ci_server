; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url-tests
  (:require
    [cider-ci.utils.url :refer :all]
    [clojure.test :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(deftest test-dissect-url

  (testing "https github url"
    (let [dissected-url (dissect "https://USER:PASS@github.com:/cider-ci/cider-ci_builder.git")]
      (is (= (:project_namespace dissected-url) "cider-ci"))
      (is (= (:project_name dissected-url) "cider-ci_builder"))
      (is (= (:protocol dissected-url) "https"))
      (is (= (:path dissected-url) "/cider-ci/cider-ci_builder.git"))
      ))

  (testing "ssh url"
    (let [dissected-url (dissect "ssh://user@server.example.com/project-namespace/project-name.git")]
      (is (= (:project_namespace dissected-url) "project-namespace"))
      (is (= (:project_name dissected-url) "project-name"))
      (is (= (:protocol dissected-url) "ssh"))
      (is (= (:path dissected-url) "/project-namespace/project-name.git"))
      ))

  (testing "ssh-scp url"
    (let [dissected-url (dissect "git@github.com:project-namespace/project-name.git")]
      (is (= (:project_namespace dissected-url) "project-namespace"))
      (is (= (:project_name dissected-url) "project-name"))
      (is (= (:protocol dissected-url) "ssh"))
      (is (= (:host dissected-url) "github.com"))
      (is (= (:username dissected-url) "git"))
      (is (= (:path dissected-url) "project-namespace/project-name.git"))
      ))
  )
