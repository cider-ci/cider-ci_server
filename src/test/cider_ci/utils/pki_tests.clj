(ns cider-ci.utils.pki-tests
  (:require
    [clojure.test :refer :all]
    [cider-ci.utils.pki :refer :all]
    ))

(deftest test-generate-key-pair
  (testing " generate-key-pair retuns a key pair"
    (is (instance? java.security.KeyPair (generate-key-pair)))))


(deftest test-pem
  (let [key-pair (generate-key-pair 728)
        private-pem (->> ["-----BEGIN RSA PRIVATE KEY-----"
                          "MIGsAgEAAiEA4Fo4aCHlPIiZP7zFiIT5EUFWrk7DN1ikYrKJ30RoQ20CAwEAAQIg"
                          "Nane7CmPZ5dU8MX6idkkqeUlm3jTI8SoYu1psUu7RhECEQD8YWCsfBwD1+/rDYN/"
                          "tEJbAhEA45Hv69qYju7yEGA5tkHr1wIRAOHBPVetAOZTP3ITuX//P6cCEQCJlwrA"
                          "RwJGKSIh1IwQLfsxAhEAmlR+E9rjT6UrdjJCe+w2Yg=="
                          "-----END RSA PRIVATE KEY-----"] (clojure.string/join "\n"))]
    (testing "key-pair->pem-private"
      (is (instance? String (key-pair->pem-private key-pair))))
    (testing "round-trip key-pair->pem-private pem->key"
      (is (instance? java.security.KeyPair
                     (-> key-pair key-pair->pem-private pem->key))))
    (testing "parsing fixed private pem"
      (is (instance? java.security.KeyPair
                     (-> private-pem pem->key))))
    (testing "key-pair->pem-public"
      (is (instance? String (key-pair->pem-public key-pair))))
    (testing "round-trip key-pair->pem-public pem->key"
      (is (instance? java.security.PublicKey
                     (-> key-pair key-pair->pem-public pem->key))))))


(deftest test-signing
  (let [key-pair (generate-key-pair 728)
        hello-signature (signature key-pair "Hello World!")]
    (testing "signing round trip"
      (is (signature-valid? key-pair "Hello World!" hello-signature))
      (is (not (signature-valid? key-pair "Hokus Pokus" hello-signature))))))

