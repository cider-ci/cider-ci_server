(ns cider-ci.utils.git-gpg-tests
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.git-gpg :refer :all]
    ))

(deftest test-commit-signature
  (let [cat-file-commit (slurp "src/test/cider_ci/utils/git_gpg/cat-file-commit.txt")
        tempered-cat-file-commit (str cat-file-commit "HaHaHa\n")
        my-armored-pub-key (slurp "src/test/cider_ci/utils/git_gpg/mykey.asc")
        some-armored-key (slurp "src/test/cider_ci/utils/git_gpg/somekey.asc") ]
    (testing "validity of a signed commit  "
      (is (= "D180BE89E69F6399711D1590E293459CB10B0442"
             (valid-signature-fingerprint cat-file-commit my-armored-pub-key))))
    (testing "invalidity of a tempered, signed commit  "
      (is (= nil (valid-signature-fingerprint tempered-cat-file-commit my-armored-pub-key))))
    (testing "invalidity on non fitting key "
      (is (= nil (valid-signature-fingerprint cat-file-commit some-armored-key))))))
