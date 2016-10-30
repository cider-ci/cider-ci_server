(ns cider-ci.repository.test.push-hooks.github
  (:require
    [cider-ci.repository.push-hooks.github :refer :all]
    [clojure.test :refer :all]
    ))

(deftest test-hooks-url
  (testing "github hooks-url by completely dissected github url"
    (is (= "https://api.github.com/repos/cider-ci/cider-ci_demo-project-bash/hooks"
           (hooks-url {:git_url "https://github.com/cider-ci/cider-ci_demo-project-bash.git"}))))
  (testing "github hooks-url when overriding parts "
    (is (= "http://my.api.server/repos/my-namespace/my-project/hooks"
           (hooks-url
             {:git_url "https://github.com/cider-ci/cider-ci_demo-project-bash.git"
              :remote_api_endpoint "http://my.api.server"
              :remote_api_namespace "my-namespace"
              :remote_api_name "my-project" })))))

