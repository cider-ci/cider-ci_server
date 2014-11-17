; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Released under the MIT license.

(ns json-roa.ring-middleware-test
  (:require 
    [cider-ci.utils.debug :as debug]
    [ring.util.response]
    [json-roa.ring-middleware]
    [cheshire.core :as json]
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    )
  (:use clojure.test))


(deftest test-wrap-roa-json-response

  (testing "a response with body of type map including roa data"
    (let [input-response {:body {:_roa {:relations {} 
                                        :about {:version "0.0.0"}
                                        }}}
          built-response ((json-roa.ring-middleware/wrap-roa-json-response 
                            identity) input-response)]
      (logging/debug test-wrap-roa-json-response {:built-response built-response})
      (testing "the built response" 
        (is built-response)
        (let [headers (-> built-response :headers)]
          (logging/debug {:headers headers})
          (testing "has the correct header" 
            (is (re-matches #".*application\/roa\+json.*" (str headers)))))
        (let [data (-> built-response :body (json/parse-string true))] 
          (logging/debug test-wrap-roa-json-response {:data data}) 
          (testing "the parsed json data" 
            (is (map? data))
            (is (:_roa data))
            )))))
  
  (testing "a response with body of type vector including roa data"
    (let [input-response {:body [{:_roa {:relations {} 
                                        :about {:version "0.0.0"}
                                        }}]}
          built-response ((json-roa.ring-middleware/wrap-roa-json-response 
                            identity) input-response)]
      (logging/debug test-wrap-roa-json-response {:built-response built-response})
      (testing "the built response" 
        (is built-response)
        (testing "has the correct header" 
          (is (re-matches #".*application\/roa\+json.*" (-> built-response :headers str))))
        (let [data (-> built-response :body (json/parse-string true))] 
          (logging/debug test-wrap-roa-json-response {:data data}) 
          (testing "the parsed json data" 
            (is (coll? data))
            (is (-> data first :_roa))
            )))))
    
  
  (testing "a response with body of type map not including roa data"
    (let [input-response {:body {:x 5}}
          built-response ((json-roa.ring-middleware/wrap-roa-json-response 
                            identity) input-response)]
      (testing "has not been modified at all" 
        (is (= input-response built-response)))))

  (testing "a response with body of type vector not including roa data"
    (let [input-response {:body [:x]}
          built-response ((json-roa.ring-middleware/wrap-roa-json-response 
                            identity) input-response)]
      (testing "has not been modified at all" 
        (is (= input-response built-response)))))


  )

