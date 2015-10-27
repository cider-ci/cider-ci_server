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
    [cider-ci.utils.config :as config :refer [get-config]]
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
    [me.raynes.fs :as clj-fs]
    [ring.middleware.cookies :as cookies]
    [ring.util.response]
    )
  (:use
    [cider-ci.storage.shared :only [delete-file delete-row delete-file-and-row]]
    ))

;### new helper ###############################################################

(defn find-store [request]
  (let [prefix (-> request :route-params :prefix)]
    (->> (get-config)
         :services :storage :stores
         (filter #(= (str "/" prefix) (:url_path_prefix %)))
         first)))

(defn get-table-and-id-name [request]
  (let [prefix (-> request :route-params :prefix)]
    (case prefix
      "tree-attachments" ["tree_attachments" "tree_id"]
      "trial-attachments" ["trial_attachments" "trial_id"])))

(defn get-row [request]
  (logging/info 'get-row {:request request})
  (let [path (-> request :route-params :*)
        id (-> request :route-params :id)
        [table-name id-name] (get-table-and-id-name request)
        query [(str "SELECT * FROM " table-name
                    "  WHERE " id-name " = ? AND path = ?") id path]]
    (-> (jdbc/query (rdbms/get-ds) query)
        first)))

(defn save-file-and-presist-row [request store]
  (let [id (java.util.UUID/randomUUID)
        file-path (str (:file_path store) "/" id)
        file (io/file file-path)
        {content-type :content-type content-length :content-length} request]
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (with-open [in (io/input-stream (:body request))
                  out (io/output-stream file)]
        (clojure.java.io/copy in out))
      (let [[table-name id-name] (get-table-and-id-name request)
            path-id (-> request :route-params :id)
            path (-> request :route-params :*)]
        (jdbc/insert! tx table-name
                      {:id id
                       id-name path-id
                       :path path
                       :content_length (.length file)
                       :content_type (or content-type "application/octet-stream")})))))


;### actions ##################################################################

(defn put-file [request]
  (logging/debug put-file [request])
  (if-let [store (find-store request)]
    (do (when-let [row (get-row request)]
          (delete-file-and-row store row))
        (if (save-file-and-presist-row request store)
          {:status 204}
          {:status 500 :body "Save failed"}))
    {:status 422
     :body "The store for this path could not be found!"}))

(defn get-file [request]
  (catcher/wrap-with-suppress-and-log-warn
    (when-let [store (find-store request)]
      (when-let [row (get-row request)]
        (let [file-path (-> (str (:file_path store) "/" (:id row)) clj-fs/file
                            clj-fs/absolute clj-fs/normalized .getAbsolutePath)]
          (-> (ring.util.response/file-response file-path)
              (ring.util.response/header "X-Sendfile" file-path)
              (ring.util.response/header "content-type" (:content_type row))))))))

(defn build-routes []
  (-> (cpj/routes
        (cpj/GET "/:prefix/:id/*" _ get-file)
        (cpj/PUT "/:prefix/:id/*" _ put-file))
      routing/wrap-shutdown))


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

(defn initialize []
  (let [http-conf (-> (get-config) :services :storage :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
