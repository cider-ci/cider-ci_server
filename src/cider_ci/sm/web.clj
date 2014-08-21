; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.sm.web
  (:require 
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.with :as with]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.middleware.cookies :as cookies]
    [ring.util.response]
    )
  (:use 
    [cider-ci.sm.shared :only [delete-file delete-row delete-file-and-row]]
    )
  )


(defonce conf (atom {}))


;### helpers ##################################################################
(defn without-prefix 
  "Returns the reminder of the string s without the prefix p. 
  Returns nil if p is not a prefix of s."
  [s p]
  (let [length-s (count s)
        length-p (count p)]
    (when (>= length-s length-p)
      (let [ s-prefix (clojure.string/join (take length-p s))
            s-postix (clojure.string/join (drop length-p s))]
        (when (= s-prefix p)
          s-postix)))))

(defn first-matching-store [url-path]
  (->> (:stores @conf)
       (some (fn [store]
               (let [url-path-prefix (:url_path_prefix store)
                     path (without-prefix url-path url-path-prefix)]
                 (when (not (clojure.string/blank? path))
                   store))))))

(defn store-and-url-path [request]
  (let [url-path (:* (:route-params request))]
    (when-let [store (first-matching-store url-path)]
      [store url-path])))

(defn get-row [store url-path]
  (let [query-str (str "SELECT * FROM " (:db_table store) " WHERE path = ? ")
        path (without-prefix url-path (:url_path_prefix store))]
    (logging/debug {:query-str query-str})
    (first (jdbc/query (:ds @conf) [query-str path]))))

(defn save-file-and-presist-row [request store url-path]
  (let [id (java.util.UUID/randomUUID)
        file-path (str (:file_path store) "/" id)
        file (io/file file-path)
        {content-type :content-type content-length :content-length} request]
    (jdbc/with-db-transaction [tx (:ds @conf)]
      (with-open [in (io/input-stream (:body request))
                  out (io/output-stream file)]
        (clojure.java.io/copy in out))
      (jdbc/insert! tx (:db_table store)
                    {:id id 
                     :path (without-prefix url-path (:url_path_prefix store))
                     :content_length (.length file)
                     :content_type (or content-type "application/octet-stream")}))))


;### actions ##################################################################
(defn put-file [request]
  (logging/debug put-file [request])
  (if-let [[store url-path] (store-and-url-path request)]
    (if (get-row store url-path)
      {:status 409
       :body "An artifact with this path exists already. Did you meant to replace it? Use DELETE and try again!"}
      (and (save-file-and-presist-row request store url-path) 
           {:status 204}))
    {:status 422
     :body "The store for this path could not be found!"}))

(defn get-file [request]
  (logging/debug get-file [request])
  (with/suppress-and-log-warn 
    (when-let [[store url-path] (store-and-url-path request)]
      (when-let [storage-row (get-row store url-path)]
        (let [file-path (str (:file_path store) "/" (:id storage-row))]
          (-> (ring.util.response/file-response file-path) 
              (ring.util.response/header "X-Sendfile" file-path)
              (ring.util.response/header "content-type" (:content_type storage-row))))))))

(defn delete [request]
  (if-let [[store url-path] (store-and-url-path request)]
    (if-let [row (get-row store url-path)]
      (and (delete-file-and-row store row)
           {:status 204})
      {:status 204})
    {:status 422
     :body "The store for this path could not be found!"}))

(defn build-routes [context]
  (cpj/routes 
    (cpj/context context []
                 (cpj/GET "*" request
                          (get-file request)) 
                 (cpj/PUT "*" request
                          (put-file request)) 
                 (cpj/DELETE "*" request
                             (delete request))
                 )))


;##### require ci or user ######################################################
(defn return-authenticate! [request]
  {:status 401
   :headers 
   {"WWW-Authenticate" 
    "Basic realm=\"Cider-CI; sign in or provide credentials\""}
   })

(defn require-ci-or-user [request handler] 
  (cond
    (:authenticated-user request) (handler request)
    (:authenticated-application request) (handler request) 
    :else (return-authenticate! request)))

(defn wrap-auth-require-ci-or-user [handler]
  (fn [request]
    (require-ci-or-user request handler)))


;#### main handler ################################################################
(defn wrap-debug-logging [handler]
  (fn [request]
    (let [wrap-debug-logging-level (or (:wrap-debug-logging-level request) 0 )]
      (logging/debug "wrap-debug-logging " wrap-debug-logging-level " request: " request)
      (let [response (handler (assoc request :wrap-debug-logging-level (+ wrap-debug-logging-level 1)))]
        (logging/debug  "wrap-debug-logging " wrap-debug-logging-level " response: " response)
        response))))

(defn build-main-handler []
  ( -> (cpj.handler/api (build-routes (str (:context (:web @conf))
                                           (:subcontext (:web @conf)))))
       (wrap-debug-logging)
       (wrap-auth-require-ci-or-user)
       (wrap-debug-logging)
       (http-basic/wrap)
       (wrap-debug-logging)
       (session/wrap)
       (wrap-debug-logging)
       (cookies/wrap-cookies)
       (wrap-debug-logging)
       ))


;#### init ####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf)
  ;(.mkdirs (io/file (attachments-dir-path)))
  (http-server/start @conf (build-main-handler)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
