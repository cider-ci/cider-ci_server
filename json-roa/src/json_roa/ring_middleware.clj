; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Released under the MIT license.

(ns json-roa.ring-middleware 
  (:require 
    [ring.util.response :as response]
    [clojure.tools.logging :as logging]
    [cheshire.core :as json]
    [clojure.walk]
    ))

(defn- build-roa-json-response [response body]
  (-> response
      (assoc :body (json/generate-string body))
      (response/header "Content-Type" "application/roa+json")
      (response/charset "UTF8")))

(defn- check-and-build-roa-json-response [response]
  (if-not (coll? (:body response))
    response
    (if-let [body (-> response :body clojure.walk/keywordize-keys)]
      (if (or (and (map? body) (:_roa body))
              (and (coll? body) (-> body first :_roa)))
        (build-roa-json-response response body)
        response)
      response)))

(defn wrap-roa-json-response 
  "Ring middleware that converts responses with a body of a map or a vector 
  including roa+json into a ROA+JSON response."
  ([handler]
   (fn [request]
     (let [response (handler request)]
       (check-and-build-roa-json-response response)))))

