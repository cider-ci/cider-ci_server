(ns cider-ci.utils.tests.require
  (:require
    [clojure.test :refer :all]

    [cider-ci.utils.config]
    [cider-ci.utils.daemon]
    [cider-ci.utils.duration]
    [cider-ci.utils.fs]
    [cider-ci.utils.http]
    [cider-ci.utils.http-server]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.map]
    [cider-ci.utils.nrepl]
    [cider-ci.utils.rdbms]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing]
    [cider-ci.utils.system]

    ))

(deftest require-namespaces
  (is true))
