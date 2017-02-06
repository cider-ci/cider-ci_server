; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.put-file
  (:require
    [cider-ci.storage.web.public :as public]
    [cider-ci.storage.shared :refer :all]

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
    [cider-ci.open-session.cors :as cors]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]

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
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))

(defn body-input-stream [request]
  (try (io/input-stream (:body request))
       (catch Exception _
         (logging/warn "Failed to open body for file put request: " request))))

(defn save-file-and-presist-row [request store]
  (let [id (java.util.UUID/randomUUID)
        file-path (str (:file_path store) "/" id)
        file (io/file file-path)
        content-type (-> request :headers (get "content-type"))
        {content-length :content-length} request]
    (jdbc/with-db-transaction [tx (get-ds)]
      (with-open [in (body-input-stream request)
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
              (jdbc/insert! (get-ds) :trial_issues
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
              (jdbc/insert! (get-ds) :job_issues
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

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'save-file-and-presist-row)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
