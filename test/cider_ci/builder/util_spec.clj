(ns cider-ci.builder.util-spec
  (:require
    [clj-yaml.core :as yaml]
    [cider-ci.builder.main :as main]
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    )
  (:use 
    [cider-ci.builder.util]
    [midje.sweet]))

(def x42-uuid 
  (java.util.UUID/fromString "cb993bdb-3a90-5842-8e12-4236ba30e276" )
  )

(def two-x42-combined 
  (java.util.UUID/fromString "41161785-032e-5593-9222-505e16fc8467"))


(facts "id-hash" 
       (fact (id-hash {:x 42}) =>  x42-uuid))


(facts idid2id 
       (fact "combining 2 uuids" (idid2id x42-uuid x42-uuid) => two-x42-combined)
       (fact "combining string and uuid" (idid2id (str x42-uuid) x42-uuid) => two-x42-combined)
       )


