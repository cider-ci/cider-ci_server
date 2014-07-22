; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.web
  (:require 
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.middleware.json]
    [ring.util.response]
    ))

;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(defonce conf (atom {}))

(defn without-prefix 
  "Returns the reminder of the string s without the prefix p. 
  Returns nil if p is not a prefix of s."
  [s p]
  (let [length-s (count s)
        length-p (count p)]
    (when (>= length-s length-p)
      (let [ s-prefix (clojure.string/join (take length-p s))
            s-postix (clojure.string/join (drop length-p s))]
        (when (= s-prefix )
          s-postix)))))


;(without-prefix "/abc/" "/abc/")
;(clojure.string/blank? "")
;(clojure.string/blank? nil)


(defn first-matching-store [url-path]
  (->> (:stores @conf)
       (some (fn [store]
               (let [url-path-prefix (:url_path_prefix store)
                     path (without-prefix url-path url-path-prefix)]
                 (when (not (clojure.string/blank? path))
                   store))))))

(defn put-file [request]
  (logging/debug put-file [request])
  (let [url-path (:* (:route-params request))]
    (logging/debug {:url-path url-path})
    (when-let [store (first-matching-store url-path)]
      (logging/debug "PUT FILE!")
      (let [id (java.util.UUID/randomUUID)
            file-path (str (:file_path store) "/" id)
            file (io/file file-path)
            {content-type :content-type content-length :content-length} request]
        (jdbc/with-db-transaction [tx (:ds @conf)]
          (with-open [in (io/input-stream (:body request))
                      out (io/output-stream file)]
            (jdbc/insert! tx (:db_table store)
                          {:id id 
                           :path (without-prefix url-path (:url_path_prefix store))
                           :content_length content-length
                           :content_type (or content-type "application/octet-stream")})
            (clojure.java.io/copy in out)))
        (logging/debug "DONE!")
        {:status 204}))))

(defn get-file [request]
  (logging/debug get-file [request])
  (with/suppress-and-log-warn 

    (let [url-path (:* (:route-params request))]
      (logging/debug {:url-path url-path})
      (when-let [store (first-matching-store url-path)]
        (let [query-str (str "SELECT * FROM " (:db_table store) " WHERE path = ? ")
              path (without-prefix url-path (:url_path_prefix store))]
          (logging/debug {:query-str query-str})
          (when-let [storage-row (first (jdbc/query (:ds @conf) [query-str path]))]
            (let [file-path (str (:file_path store) "/" (:id storage-row))]
              (-> (ring.util.response/file-response file-path) 
                  (ring.util.response/header "X-Sendfile" file-path)
                  (ring.util.response/header "content-type" (:content_type storage-row))))
            ))))))

(defn log-handler [handler level]
  (fn [request]
    (logging/debug "log-handler " level " request: " request)
    (let [response (handler request)]
      (logging/debug  "log-handler " level " response: " response)
      response)))


(defn build-routes [context]
  (cpj/routes 
    (cpj/context context []
                 (cpj/GET "*" request
                          (get-file request)) 
                 (cpj/PUT "*" request
                          (put-file request)) 
                 )))

(defn authenticate [handler]
  (fn [request]
    (logging/debug "authenticate handler" {:request request})
    (if (= (:request-method request) :get)
      (handler request)
      ((http/authenticate handler) request))))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (:context (:web @conf))))
       (log-handler 2)
       (ring.middleware.json/wrap-json-params)
       (log-handler 1)
       (authenticate)
       (log-handler 0)
       ))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  ;(.mkdirs (io/file (attachments-dir-path)))
  (http-server/start @conf (build-main-handler)))

