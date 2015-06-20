; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.web
  (:require
    [cider-ci.auth.core :as auth]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.open-session.cors :as cors]
    [cider-ci.storage.web.public :as public]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.routing :as routing]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    [drtom.logbug.ring :refer [wrap-handler-with-logging]]
    [ring.middleware.cookies :as cookies]
    [ring.util.response]
    )
  (:use
    [cider-ci.storage.shared :only [delete-file delete-row delete-file-and-row]]
    ))


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
  (->> (-> @conf :services :storage :stores)
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
    (first (jdbc/query (rdbms/get-ds) [query-str path]))))

(defn save-file-and-presist-row [request store url-path]
  (let [id (java.util.UUID/randomUUID)
        file-path (str (:file_path store) "/" id)
        file (io/file file-path)
        {content-type :content-type content-length :content-length} request]
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
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
    (do (when-let [row (get-row store url-path)]
          (delete-file-and-row store row))
      (if (save-file-and-presist-row request store url-path)
        {:status 204}
        {:status 500 :body "Save failed"}))
    {:status 422
     :body "The store for this path could not be found!"}))

(defn get-file [request]
  (logging/debug get-file [request])
  (catcher/wrap-with-suppress-and-log-warn
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

(defn build-routes []
  (-> (cpj/routes
        (cpj/GET "*" request
                 (get-file request))
        (cpj/PUT "*" request
                 (put-file request))
        (cpj/DELETE "*" request
                    (delete request)))
      routing/wrap-shutdown ))


;##### status dispatch ########################################################

(defn status-handler [request]
  (let [stati {:rdbms (rdbms/check-connection)}]
    (if (every? identity (vals stati))
      {:status 200
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} }
      {:status 511
       :body (json/write-str stati)
       :headers {"content-type" "application/json;charset=utf-8"} })))

(defn wrap-status-dispatch [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))



;#### routing #################################################################

(defn build-base-handler []
  ( -> (build-routes)
       cpj.handler/api
       (wrap-handler-with-logging 'cider-ci.storage.web)
       wrap-status-dispatch
       (wrap-handler-with-logging 'cider-ci.storage.web)
       ))

(defn wrap-auth [handler]
  (-> handler
      (wrap-handler-with-logging 'cider-ci.storage.web)
      auth/wrap-authenticate-and-authorize-service-or-user
      (wrap-handler-with-logging 'cider-ci.storage.web)
      (http-basic/wrap {:user true :service true :executor true})
      (wrap-handler-with-logging 'cider-ci.storage.web)
      session/wrap
      (wrap-handler-with-logging 'cider-ci.storage.web)
      cookies/wrap-cookies
      (wrap-handler-with-logging 'cider-ci.storage.web)
      cors/wrap
      (wrap-handler-with-logging 'cider-ci.storage.web)
      (public/wrap-shortcut handler)
      ))

(defn build-main-handler [context]
  (-> (build-base-handler)
      (wrap-handler-with-logging 'cider-ci.storage.web)
      wrap-auth
      (wrap-handler-with-logging 'cider-ci.storage.web)
      (routing/wrap-prefix context)
      (wrap-handler-with-logging 'cider-ci.storage.web)
      routing/wrap-log-exception
      ))


;#### the server ##############################################################

(defn initialize [new-conf]
  (reset! conf new-conf)
  (let [http-conf (-> @conf :services :storage :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
