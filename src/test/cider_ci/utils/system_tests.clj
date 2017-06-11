(ns cider-ci.utils.system-tests
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.system :refer :all]
    ))


(deftest test-async-exec
  (testing "echo hello world"
    (let [async-exec-hello-world (async-exec ["echo" "Hello World!"] {:dir "/tmp"})]
      (testing "std out"
        (is (= "Hello World!"
               (-> async-exec-hello-world deref :exec deref :out  clojure.string/trim)))
        (testing "it exits with zero state"
          (is (= 0 (-> async-exec-hello-world deref :exec deref :exit))))
        )))
  (testing "calling cancle-async-exec"
    (let [async-exec-sleep-60-canceled (async-exec ["sleep" "60"] {:dir "/tmp"})]
      (cancle-async-exec async-exec-sleep-60-canceled)
      (testing "it didn't exit with zero"
        (is (not= 0 (-> async-exec-sleep-60-canceled deref :exec deref :exit))))
      (testing "there is a throwable exception attached"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"has been canceled"
                              (-> async-exec-sleep-60-canceled deref :exception throw))))))
  )

(deftest test-exec!

  (is (= "Hello World!"
         (clojure.string/trim (:out (exec! ["echo" "Hello World!"] {:dir "/tmp"})))))

  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo #"non zero status"
        (exec! ["exit" "1"])))

  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo #"timed out"
        (exec! ["sleep" "10"] {:timeout "3 Seconds"})))

  )
