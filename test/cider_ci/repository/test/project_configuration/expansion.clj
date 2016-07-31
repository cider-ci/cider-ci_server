(ns cider-ci.repository.test.project-configuration.expansion
  (:require
    [cider-ci.repository.project-configuration.expansion :refer :all]
    [clojure.test :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]

    ))

(def paths
  { "main-git-ref" {"m1.yml" "{a: 1, b: 2, c: 3}"
                    "m2.yml" "{a: 5, c: 7}"

                    ; subinclude
                    "sub-include-m1-then-m2.yml" "{include: [m1.yml, m2.yml]}"

                    ; submodule
                    "with-submodule-include.yml" "{include: [{:path m1.yml}, {:path m-in-submodule.yml, submodule: [submodule]}]}"

                    ; merging
                    "include-m1-with-overriding-a.yml" "{include: [m1.yml], a: this-will-be-overridden}"
                    "include-m2-with-overriding-c.yml" "{include: [m2.yml], d: 44}"
                    "merging-includes.yml" "{include: [include-m1-with-overriding-a.yml, include-m2-with-overriding-c.yml]}" }
   "submodule-git-ref" { "m-in-submodule.yml" "{a: 15}"} })

(defn get-inclusion-moc
  ([include-spec]
   (get-inclusion-moc nil include-spec))
  ([git-ref-id include-spec]
   ;(logging/info 'get-inclusion-moc [git-ref-id include-spec])
   (let [submodule (or (:submodule include-spec) [])]
     (-> paths
         (get submodule)
         (get (:path include-spec))))))

(defn resolve-submodule-git-ref [git-refs paths]
  (if (and (= git-refs ["main-git-ref"])
           (= paths ["submodule"]))
    "submodule-git-ref"
    "main-git-ref"))


(defn get-content [git-ref path submodule-path]
  (-> paths
      (get git-ref)
      (get path)))

(deftest hot-spot
  (with-redefs [cider-ci.repository.project-configuration.shared/get-content
                (fn [& args] (apply get-content args))
                cider-ci.repository.project-configuration.shared/resolve-submodule-git-ref
                (fn [& args] (apply resolve-submodule-git-ref args))]

    ))


(deftest test-expand
  (with-redefs [cider-ci.repository.project-configuration.shared/get-content
                (fn [& args] (apply get-content args))
                cider-ci.repository.project-configuration.shared/resolve-submodule-git-ref
                (fn [& args] (apply resolve-submodule-git-ref args))]

    (testing "a simple inclusion m1"
      (is (= (expand nil {:include "m1.yml"})
             {:a 1 :b 2 :c 3})))

    (testing "a overriding values of first include with values of second include but retaining non overridden values"
      (is (= (expand nil {:include ["m1.yml" "m2.yml"]})
             {:a 5 :b 2 :c 7})))

    (testing "local values override any included value no matter if defined before oder after include"
      (is (= (expand nil {:a 42 :include ["m1.yml" "m2.yml"] :c 33})
             {:a 42 :b 2 :c 33})))

    (testing "sub include"
      (is (= (expand "bogus-git-ref" {:include "sub-include-m1-then-m2.yml"})
             {:a 5 :b 2 :c 7})))

    (testing "submodule include"
      (is (= (expand "main-git-ref" {:include ["m1.yml", {:path "m-in-submodule.yml" :submodule ["submodule"]}]})
             {:a 15, :b 2, :c 3})))

    (testing "merging: simulates the include to the same level from different branches"
      (is (= (expand "main-git-ref" {:include "merging-includes.yml"})
             {:a 5 :b 2 :c 7 :d 44})))
    ))


;(debug/debug-ns *ns*)
