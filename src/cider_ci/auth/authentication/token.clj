; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication.token
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.users.api-tokens.core :refer [hash-string]]

    [honeysql.core :as sql]
    [pandect.algo.sha256 :as algo.sha256]
    [clojure.data.codec.base64 :as codec.base64]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(defn find-user-token-query [hashed-tokens]
  (-> (sql/select :users.*
                  :scope_read :scope_write :scope_admin_read :scope_admin_write
                  [:revoked :token_revoked]
                  [:description :token_description])
      (sql/from :api_tokens)
      (sql/merge-where (if (empty? hashed-tokens)
                         false
                         [:in :api_tokens.token_hash hashed-tokens]))
      (sql/merge-where [:<> :api_tokens.revoked true])
      (sql/merge-where (sql/raw "now() < api_tokens.expires_at"))
      (sql/merge-join :users [:= :users.id :api_tokens.user_id])
      (sql/format)))

(defn find-user-token [hashed-tokens]
  (->> (find-user-token-query hashed-tokens)
       (jdbc/query (rdbms/get-ds))
       first))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-token-secret-in-basic-auth-username [request]
  (-> request :basic-auth :username))

(defn find-token-secret-in-basic-auth-password [request]
  (-> request :basic-auth :password))

(defn find-token-secret-in-header [request]
  (:token-auth request))

(defn find-and-authenticate-token-secret-or-continue [handler request]
  (handler
    (if-let [user-token
             (find-user-token
               (->> [(find-token-secret-in-header request)
                     (find-token-secret-in-basic-auth-username request)
                     (find-token-secret-in-basic-auth-password request)]
                    (map presence) (filter identity) (map hash-string)))]
      (assoc request :authenticated-entity
             (assoc user-token
                    :type :user
                    :method :token
                    :scope_admin_read (and (:scope_admin_read user-token)
                                           (:is_admin user-token))
                    :scope_admin_write (and (:scope_admin_write user-token)
                                            (:is_admin user-token))))
      request)))


(defn wrap-authenticate [handler]
  (fn [request]
    (find-and-authenticate-token-secret-or-continue handler request)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'authenticate-token-header)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
