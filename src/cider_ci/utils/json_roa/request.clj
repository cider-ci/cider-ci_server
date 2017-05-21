; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Released under the MIT license.

(ns cider-ci.utils.json-roa.request
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clojure.data.json :as json]
    [ring.util.response]
    [ring.middleware.accept]

    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :as logbug-ring :refer [wrap-handler-with-logging]]
    [clojure.tools.logging :as logging]
    ))

(defn- default-json-decoder [json-str]
  (json/read-str json-str :key-fn keyword))

(defn- dispatch [json-roa-handler request handler]
  (let [response (handler request)]
    (if (= (-> request :accept :mime) :json-roa)
      (json-roa-handler request response)
      response)))

(defn- wrap-dispatch [handler json-roa-handler]
  (fn [request] (dispatch json-roa-handler request handler)))

;### accept ###################################################################

(defn not-acceptable [request]
  (->
    {:status 406
     :body "This resource accepts 'application/json-roa+json' or 'application/json' only."}
    (ring.util.response/header "Content-Type" "text/plain")
    (ring.util.response/charset "UTF-8")))

(defn accept [request handler]
  (case (-> request :accept :mime)
    :json-roa (handler request)
    :json (handler request)
    (not-acceptable request)))

(defn wrap-accept [handler]
  (fn [request] (accept request handler)))

(defn wrap-error [h]
  (fn [r] (try (h r)
               (catch Exception e
                 (logging/error e)
                 (throw e)))))
;### wrap #####################################################################

(defn wrap [handler json-roa-handler
            & {:keys [json-decoder] :or {json-decoder default-json-decoder}}]
  (I> wrap-handler-with-logging
      handler
      (wrap-dispatch json-roa-handler)
      wrap-accept
      wrap-error))


;(debug/debug-ns *ns*)
