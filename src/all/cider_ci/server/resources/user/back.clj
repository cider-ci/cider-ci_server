(ns cider-ci.server.resources.user.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.utils.honeysql :as sql]

    [clojure.set :refer [rename-keys]]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    )
  (:import
    [java.awt.image BufferedImage]
    [java.io ByteArrayInputStream ByteArrayOutputStream]
    [java.util Base64]
    [javax.imageio ImageIO]
    ))

;;; data keys ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def user-selects
  [:id                       
   :created_at               
   :is_admin                 
   :name
   :password_sign_in_enabled 
   :primary_email_address    
   :sign_in_enabled 
   :updated_at])

(def user-write-keys
  [:is_admin                 
   :name
   :password_hash
   :password_sign_in_enabled 
   :primary_email_address    
   :sign_in_enabled 
   :sign_in_enabled ])

(def user-write-keymap
  {})


;;; user ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn user-query [user-id]
  (-> (apply sql/select user-selects)
      (sql/from :users)
      (sql/merge-where [:= :id user-id])
      sql/format))

(defn user [{tx :tx {user-id :user-id} :route-params}]
  {:body
   (first (jdbc/query tx (user-query user-id)))})

;;; delete user ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete-user [{tx :tx {user-id :user-id} :route-params}]
  (if (= [1] (jdbc/delete! tx :users ["id = ?" user-id]))
    {:status 204}
    {:status 404 :body "Delete user failed without error."}))

;;; transfer data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn transfer-data
  ([{tx :tx {user-id :user-id target-user-id :target-user-id} :route-params}]
   (transfer-data user-id target-user-id tx))
  ([user-id target-user-id tx]
   (doseq [table [:access_rights :reservations :contracts :orders]]
     (jdbc/update! tx table
                   {:user_id target-user-id}
                   ["user_id = ?" user-id]))
   {:status 204}))

(defn transfer-data-and-delete-user
  ([{tx :tx {user-id :user-id target-user-id :target-user-id} :route-params}]
   (transfer-data-and-delete-user user-id target-user-id tx))
  ([user-id target-user-id tx]
   (transfer-data user-id target-user-id tx)
   (if (= [1] (jdbc/delete! tx :users ["id = ?" user-id]))
     {:status 204}
     {:status 404 :body "Delete user failed without error."})))


;;; password ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn password-hash
  ([password tx]
   (->> ["SELECT crypt(?, gen_salt('bf', 10)) AS password_hash" password]
        (jdbc/query tx)
        first :password_hash)))

(defn insert-pw-hash [data tx]
  (if-let [password (-> data :password presence)]
    (assoc data :password_hash (password-hash password tx))
    data))


;;; update user ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn prepare-write-data [data tx]
  (catcher/with-logging
    {}
    (-> data
        (insert-pw-hash tx)
        (select-keys user-write-keys)
        (rename-keys user-write-keymap))))

(defn patch-user
  ([{tx :tx data :body {user-id :user-id} :route-params}]
   (patch-user user-id (prepare-write-data data tx) tx))
  ([user-id data tx]
   (when (->> ["SELECT true AS exists FROM users WHERE id = ?" user-id]
              (jdbc/query tx )
              first :exists)
     (jdbc/update! tx :users data ["id = ?" user-id])
     {:status 204})))


;;; create user ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-user
  ([{tx :tx data :body}]
   (create-user (prepare-write-data data tx) tx))
  ([data tx]
   (let [email-address (:primary_email_address data)
         email-address-row (jdbc/insert! tx :email_addresses 
                                         {:user_id nil
                                          :email_address email-address})
         user (first (jdbc/insert! tx :users data))
         id (:id user)]
     (jdbc/update! tx :email_addresses {:user_id id}
                   ["email_address = ?" email-address])
     {:body user})))

;;; routes and paths ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def user-path (path :user {:user-id ":user-id"}))

(def routes
  (cpj/routes
    (cpj/GET user-path [] #'user)
    (cpj/PATCH user-path [] #'patch-user)
    (cpj/DELETE user-path [] #'delete-user)
    (cpj/POST (path :users) [] #'create-user)))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'data-url-img->buffered-image)
;(debug/wrap-with-log-debug #'buffered-image->data-url-img)
;(debug/wrap-with-log-debug #'resized-img)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
