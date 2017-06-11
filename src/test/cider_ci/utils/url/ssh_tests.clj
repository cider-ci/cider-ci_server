(ns cider-ci.utils.url.ssh-tests
  (:require
    [cider-ci.utils.url.ssh :refer :all]
    [clojure.test :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))


(deftest test-dissect
  (testing "dissect with relative hostname "
    (let [dissected (dissect "ssh://user@server.example.com/~/project-namespace/project-name.git")]
      (is (= (:protocol dissected) "ssh"))
      (is (= (:username dissected) "user"))
      (is (= (:host dissected) "server.example.com"))
      (is (= (:path dissected) "/~/project-namespace/project-name.git"))
      (is (= (:project_namespace dissected) "project-namespace"))
      (is (= (:project_name dissected) "project-name"))))

  (testing "downcasing protocol"
    (let [dissected (dissect "SSH://user@server/foo/bar/baz")]
      (is (= (:protocol dissected) "ssh"))))

  (testing "dissect with ipv6 hostname"
    (let [dissected (dissect "ssh://user@[1080::8:800:200C:417A]/~/project-namespace/project-name.git")]
      (is (= (:username dissected) "user"))
      (is (= (:host dissected) "[1080::8:800:200C:417A]"))
      (is (= (:path dissected) "/~/project-namespace/project-name.git"))
      )))


