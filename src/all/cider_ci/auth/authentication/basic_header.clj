; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication.basic-header

  (:require
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:import
    [java.util Base64]
    [com.google.common.io BaseEncoding]
    ))

(defn- decode-base64
  [^String string]
  (apply str (map char (.decode (Base64/getDecoder) (.getBytes string)))))

(defn add-basic-auth-properties [request]
  (or (when-let [auth-header (-> request :headers :authorization)]
        (when-let [basic-encoded (last (re-find #"(?i)^basic (.*)$" auth-header))]
          (catcher/snatch
            {:level :debug
             :return-expr {:status 422
                           :body "Failure extracting basic auth values!"}}
            (let [[username password] (-> basic-encoded decode-base64
                                          (clojure.string/split #":" 2))]
              (assoc request
                     :basic-auth
                     {:username username
                      :password password})))))
      request))

(defn extract [request handler]
  (-> request add-basic-auth-properties handler))

(defn wrap-extract [handler]
  "Extracts information from the \"Authorization: Basic...\" header and adds
  :basic-auth {:name name :password password}.  Continues if no such header is
  found.  Catches and returns 422 if the header is found but extraction fails."
  (fn [request] (extract request handler)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


