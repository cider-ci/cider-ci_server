(ns cider-ci.utils.tests.require
  (:require
    [clojure.test :refer :all]

    [cider-ci.auth.authorize]
    [cider-ci.auth.http_basic]
    [cider-ci.auth.session]

    [cider-ci.utils.app]
    [cider-ci.utils.config]
    [cider-ci.utils.core]
    [cider-ci.utils.daemon]
    [cider-ci.utils.duration]
    [cider-ci.utils.fs]
    [cider-ci.utils.http]
    [cider-ci.utils.http-server]
    [cider-ci.utils.include-exclude]
    [cider-ci.utils.jdbc]
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.map]
    [cider-ci.utils.nrepl]
    [cider-ci.utils.pending-rows]
    [cider-ci.utils.rdbms]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing]
    [cider-ci.utils.row-events]
    [cider-ci.utils.runtime]
    [cider-ci.utils.self]
    [cider-ci.utils.status]
    [cider-ci.utils.system]
    [cider-ci.utils.ubiquitous]
    [cider-ci.utils.url]

    ))

(deftest require-namespaces
  (is true))
