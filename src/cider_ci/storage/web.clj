; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.web
  (:require
    [cider-ci.storage.web.public :as public]
    [cider-ci.storage.shared :refer [delete-file delete-row delete-file-and-row]]

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.open-session.cors :as cors]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.runtime :as runtime]

    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [honeysql.sql :refer :all]
    [me.raynes.fs :as clj-fs]
    [ring.middleware.cookies :as cookies]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [÷> ÷>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    )
  )

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


;### put-authorization ########################################################

(defn valid-token-for-tree? [tree-id token]
  (let [query (-> (sql-select true)
                  (sql-from :trials)
                  (sql-merge-where [:= :token token])
                  (sql-merge-where (sql-raw "(trials.updated_at > (now() - interval '24 Hours'))"))
                  (sql-merge-join :tasks [:= :tasks.id :trials.task_id])
                  (sql-merge-join :jobs [:= :jobs.id :tasks.job_id])
                  (sql-merge-where [:= :jobs.tree_id tree-id])
                  sql-format)]
    (->> query (jdbc/query (get-ds)) first)))

(defn valid-token-for-trial? [trial-id token]
  (let [query (-> (sql-select true)
                  (sql-from :trials)
                  (sql-merge-where [:= :token token])
                  (sql-merge-where [:= :id trial-id])
                  (sql-merge-where (sql-raw "(trials.updated_at > (now() - interval '24 Hours'))"))
                  sql-format)]
    (->> query (jdbc/query (get-ds)) first)))

(defn executor-may-upload-trial-attachments? [request]
  (or (-> request :authenticated-executor :upload_trial_attachments)
      (do (let [trial-id (-> request :route-params :id)]
            (catcher/snatch {}
              (jdbc/insert!  (rdbms/get-ds) :trial_issues
                {:trial_id trial-id
                 :id (clj-uuid/v5 clj-uuid/+null+ (str trial-id " executor-upload-forbidden"))
                 :type "warning"
                 :title "Attachment Upload Forbidden"
                 :description (str "The executor " (-> request :authenticated-executor :name)
                                   " is not allowed to upload attachments but it tried to.")})))
          false)))

(defn get-job-id-for-trial-token [trial-token]
  (->> (jdbc/query (get-ds)
                   (-> (sql-select :jobs.id)
                       (sql-from :jobs)
                       (sql-merge-join :tasks [:= :jobs.id :tasks.job_id])
                       (sql-merge-join :trials [:= :tasks.id :trials.task_id])
                       (sql-merge-where [:= :trials.token trial-token])
                       sql-format))
       first :id))

(defn executor-may-upload-tree-attachments? [request]
  (or (-> request :authenticated-executor :upload_tree_attachments)
      (do (let [trial-token (-> request :headers (get "trial-token"))
                job-id (get-job-id-for-trial-token trial-token)]
            (catcher/snatch {}
              (jdbc/insert!  (rdbms/get-ds) :job_issues
                            {:job_id job-id
                             :id (clj-uuid/v5 clj-uuid/+null+ (str job-id " executor-upload-forbidden"))
                             :type "warning"
                             :title "Attachment Upload Forbidden"
                             :description (str "The executor " (-> request :authenticated-executor :name)
                                               " is not allowed to upload attachments but it tried to.")})))
          false)))

(defn put-authorized? [store request]
  (if-let [token (-> request :headers (get "trial-token"))]
    (case (:db_table store)
      "trial_attachments"  (and (executor-may-upload-trial-attachments? request)
                                (valid-token-for-trial? (-> request :route-params :id) token))
      "tree_attachments" (and (executor-may-upload-tree-attachments? request)
                              (valid-token-for-tree? (-> request :route-params :id) token))
      )
    (do (logging/warn "token missing")
        false )))


;### actions ##################################################################

(defn put-file [request]
  (if-let [store (find-store request)]
    (if-not (put-authorized? store request)
      {:status 403}
      (do (when-let [row (get-row request)]
            (delete-file-and-row store row))
          (if (save-file-and-presist-row request store)
            {:status 204}
            {:status 500 :body "Save failed"})))
    {:status 422
     :body "The store for this path could not be found!"}))

(defn get-file [request]
  (catcher/snatch {}
    (when-let [store (find-store request)]
      (when-let [row (get-row request)]
        (let [file-path (-> (str (:file_path store) "/" (:id row)) clj-fs/file
                            clj-fs/absolute clj-fs/normalized .getAbsolutePath)]
          (-> (ring.util.response/file-response file-path)
              (ring.util.response/header "X-Sendfile" file-path)
              (ring.util.response/header "content-type" (:content_type row))))))))

(def storage-routes
  (-> (cpj/routes
        (cpj/GET "/:prefix/:id/*" _ get-file)
        (cpj/PUT "/:prefix/:id/*" _ put-file))))


;##### status dispatch ########################################################

(defn status-handler [request]
  (let [rdbms-status (rdbms/check-connection)
        memory-status (runtime/check-memory-usage)
        body (json/write-str {:rdbms rdbms-status
                              :memory memory-status})]
    {:status  (if (and rdbms-status (:OK? memory-status))
                200 499 )
     :body body
     :headers {"content-type" "application/json;charset=utf-8"} }))

(defn wrap-status-dispatch [default-handler]
  (cpj/routes
    (cpj/GET "/status" request #'status-handler)
    (cpj/ANY "*" request default-handler)))


;#### routing #################################################################

(def base-handler
  (÷> (wrap-handler-with-logging :trace)
      storage-routes
      routing/wrap-shutdown
      cpj.handler/api
      wrap-status-dispatch))

(defn wrap-auth [handler]
  (÷> (wrap-handler-with-logging :trace)
      handler
      (authorize/wrap-require! {:user true :service true :executor true})
      (http-basic/wrap {:user true :service true :executor true})
      session/wrap
      cookies/wrap-cookies
      cors/wrap
      (public/wrap-shortcut handler)))

(defn build-main-handler [context]
  (÷> (wrap-handler-with-logging :trace)
      base-handler
      wrap-auth
      (routing/wrap-prefix context)
      routing/wrap-log-exception))


;#### the server ##############################################################

(defn initialize []
  (let [http-conf (-> (get-config) :services :storage :http)
        context (str (:context http-conf) (:sub_context http-conf))]
    (http-server/start http-conf (build-main-handler context))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'executor-may-upload-tree-attachments?)
;(debug/wrap-with-log-debug #'put-authorized?)
;(debug/wrap-with-log-debug #'find-store)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
