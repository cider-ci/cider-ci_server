(ns cider-ci.server.resources.email-addresses.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.resources.api-token.back :as api-token]

    [cider-ci.utils.honeysql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(defn add-email-address 
  ([{{user-id :user-id} :route-params 
     {email-address :email_address} :body 
     tx :tx}]
   (add-email-address user-id email-address tx))
  ([user-id email-address tx]
   {:status 201
    :body (first (jdbc/insert! tx :email_addresses
                               {:user_id user-id
                                :email_address email-address}))}))
(defn set-as-primary 
  ([{{user-id :user-id
      email-address :email-address} :route-params
     tx :tx}]
   (set-as-primary user-id email-address tx))
  ([user-id email-address tx]
   (if (not (= 1 (first (jdbc/update! 
                          tx :users
                          {:primary_email_address email-address}
                          ["id = ?" user-id]))))
     (throw (ex-info "Changing the primary email address failed!" {:status 422}))
     {:status 204})))

(defn delete
  ([{{user-id :user-id
      email-address :email-address} :route-params
     tx :tx}]
   (delete user-id email-address tx))
  ([user-id email-address tx]
   (if (not (= 1 (first (jdbc/delete!
                          tx :email_addresses
                          ["user_id = ? AND email_address = ?" user-id email-address]))))
     (throw (ex-info "Deleting the email address failed!" {:status 422}))
     {:status 204})))

(defn index 
  ([{{user-id :user-id} :route-params tx :tx}]
   (index user-id tx))
  ([user-id tx]
   {:body 
    {:email_addresses
     (->> ["SELECT * FROM email_addresses WHERE user_id = ?" user-id]
          (jdbc/query tx))}}))

;##############################################################################

(def email-addresses-path
  (path :email-addresses {:user-id ":user-id"}))

(def email-addresses-add-path
  (path :email-addresses-add {:user-id ":user-id"}))

(def email-address-path 
  (path :email-address {:user-id ":user-id"
                        :email-address ":email-address"}))

(def routes
  (cpj/routes
    (cpj/GET email-addresses-path [] #'index)
    (cpj/POST email-addresses-add-path [] #'add-email-address)
    (cpj/POST email-address-path [] #'set-as-primary)
    (cpj/DELETE email-address-path [] #'delete)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
