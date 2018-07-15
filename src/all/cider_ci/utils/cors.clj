; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.cors
  (:require
    [ring.util.response :as response]))

(defn wrap [handler & {:keys [enable] :or {enable true}}]
  "This wrapper adds the Access-Control-Allow-Origin and
  Access-Control-Allow-Credentials headers to the response. The value of the
  Access-Control-Allow-Credentials is based on the first present request header
  cors-origin, origin, pair of scheme and host, or finally '*' if non of the
  previous was found."
  (fn [request]
    (let [response (handler request)]
      (if-not enable
        response
        (-> response
            (response/header
              "Access-Control-Allow-Origin" (or
                                              (-> request :headers (get "cors-origin"))
                                              (-> request :headers (get "origin"))
                                              (let [scheme  (-> request :scheme name)
                                                    host  (-> request :headers (get "host"))]
                                                (when (and scheme host) (str scheme "://" host)))
                                              "*"))
            (response/header
              "Access-Control-Allow-Credentials" "true")
            (response/header
              "Access-Control-Allow-Headers" "Content-Type, Accept, *")
            (response/header
              "Access-Control-Allow-Methods" "CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, TRACE, *")
            )))))


