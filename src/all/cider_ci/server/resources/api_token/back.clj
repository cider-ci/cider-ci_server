(ns cider-ci.server.resources.api-token.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.utils.honeysql :as sql]

    [clojure.set :refer [rename-keys]]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [crypto.random]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    )
  (:import
    [com.google.common.io BaseEncoding]
    [org.joda.time DateTime]
    [java.time OffsetDateTime]
    ))


(def api-token-selects
  [:created_at
   :description
   :expires_at
   :id
   :scope_admin_read
   :scope_admin_write
   :scope_read
   :scope_write
   :token_part
   :updated_at
   :user_id])

(def allowed-insert-and-patch-keys
  [:description
   :expires_at
   :scope_admin_read
   :scope_admin_write
   :scope_read
   :scope_write])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def b32 (BaseEncoding/base32))

(defn secret [n]
  (->> n crypto.random/bytes
       (.encode b32)
       (map char)
       (apply str)))

(defn token-hash
  ([password tx]
   (->> ["SELECT crypt(?,gen_salt('bf')) AS pw_hash" password]
        (jdbc/query tx)
        first :pw_hash)))

(defn insert-token [params tx]
  (first (jdbc/insert! tx :api_tokens params)))

(defn parse-iso8601 [s]
  (DateTime. (* 1000 (.toEpochSecond (OffsetDateTime/parse s)))))

(defn normalize-create-or-update-params [params]
  (->> params
       (map (fn [[k v]]
              (case k
                :expires_at [k (parse-iso8601 v)]
                [k v])))
       (into {})))

(defn create-api-token
  ([{body :body tx :tx {user-id :user-id} :route-params :as request}]
   (create-api-token user-id body tx))
  ([user-id body tx]
   (let [token-secret (secret 20)
         token-hash (token-hash token-secret tx)
         params (-> body
                    (select-keys allowed-insert-and-patch-keys)
                    (assoc :token_hash token-hash
                           :token_part (subs token-secret 0 5)
                           :user_id user-id)
                    normalize-create-or-update-params)
         token (insert-token params tx)]
     {:status 200
      :body (assoc token :token_secret token-secret)})))


;;; patch ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn patch
  ([{tx :tx data :body {user-id :user-id
                        api-token-id :api-token-id} :route-params}]
   (patch api-token-id user-id data tx))
  ([api-token-id user-id data tx]
   (when (->> [(str "SELECT true AS exists FROM api_tokens "
                    "WHERE id = ? AND user_id = ?") api-token-id user-id]
              (jdbc/query tx )
              first :exists)
     (jdbc/update! tx :api_tokens
                   (-> data
                       (select-keys allowed-insert-and-patch-keys)
                       normalize-create-or-update-params)
                   ["id = ? AND user_id = ? "  api-token-id user-id])
     {:status 204})))



;;; get ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn api-token-query [api-token-id user-id]
  (-> (apply sql/select api-token-selects)
      (sql/from :api_tokens)
      (sql/merge-where [:= :user_id user-id])
      (sql/merge-where [:= :id api-token-id])
      sql/format))

(defn get-api-token
  ([{tx :tx {user-id :user-id api-token-id :api-token-id} :route-params}]
   (get-api-token api-token-id user-id tx))
  ([api-token-id user-id tx]
   (when-let [api-token (->> (api-token-query api-token-id user-id)
                             (jdbc/query tx) first)]
     {:body api-token})))


;;; delete ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete[{tx :tx {user-id :user-id
                      api-token-id :api-token-id} :route-params}]
  (if (= [1] (jdbc/delete! tx :api_tokens ["user_id = ? AND id = ?"
                                           user-id api-token-id]))
    {:status 204}
    {:status 404 :body "Delete api-token-id failed without error."}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def api-token-path
  (path :api-token {:user-id ":user-id" :api-token-id ":api-token-id"}))

(def routes
  (cpj/routes
    (cpj/PATCH api-token-path [] #'patch)
    (cpj/DELETE api-token-path [] #'delete)
    (cpj/GET api-token-path [] #'get-api-token)
    (cpj/POST (path :api-tokens {:user-id ":user-id"} ) [] #'create-api-token)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'data-url-img->buffered-image)
;(debug/wrap-with-log-debug #'buffered-image->data-url-img)
;(debug/wrap-with-log-debug #'resized-img)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)

