; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.resources.gpg-keys.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.resources.api-token.back :as api-token]
    [cider-ci.utils.git-gpg :as git-gpg]

    [cider-ci.utils.honeysql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))

(defn extract-fingerprints [keystr]
  (try (->> keystr git-gpg/pub-keys
            (map git-gpg/hex-fingerprint))
       (catch Throwable e
         (throw (ex-info "Failed to extract GPG key or fingerprints." 
                         {:status 422} e)))))
       
(defn add-gpg-key
  ([{{user-id :user-id} :route-params 
     body :body 
     tx :tx}]
   (add-gpg-key (assoc body :user_id user-id) tx))
  ([data tx]
   (let [key-string (:key data)
         gpg-key-row (first (jdbc/insert! tx :gpg_keys data))
         _ (when-not (presence key-string) 
             (throw (ex-info 
                      "The GPG key data may not be empty." 
                      {:status 422})))
         fingerprints (extract-fingerprints key-string)]
     (when-not (seq fingerprints)
       (throw (ex-info 
                "No fingerprints could be extracted form this key!. " 
                {:status 422})))
     (doseq [fingerprint fingerprints]
       (jdbc/insert! tx :gpg_key_finterprints {:gpg_key_id (:id gpg-key-row)
                                               :fingerprint fingerprint}))
     {:status 201
      :body gpg-key-row})))


(defn delete
  ([{{user-id :user-id
      gpg-key-id :gpg-key-id} :route-params
     tx :tx}]
   (delete user-id gpg-key-id tx))
  ([user-id gpg-key-id tx]
   (if (not (= 1 (first (jdbc/delete!
                          tx :gpg_keys
                          ["user_id = ? AND id = ?" user-id gpg-key-id]))))
     (throw (ex-info "Deleting the GPG key failed!" {:status 422}))
     {:status 204})))

(defn index-query [user-id]
  (-> (sql/select :id :description :created_at
                  [(-> (sql/select (sql/raw "json_agg(gpg_key_finterprints.fingerprint)"))
                       (sql/from :gpg_key_finterprints)
                       (sql/merge-where 
                         [:= :gpg_key_finterprints.gpg_key_id :gpg_keys.id]))
                   :fingerprints])
      (sql/from :gpg_keys)
      (sql/merge-where [:= :user_id user-id])
      (sql/order-by [:created_at :desc])
      sql/format))

(defn index 
  ([{{user-id :user-id} :route-params tx :tx}]
   (index user-id tx))
  ([user-id tx]
   {:body 
    {:gpg_keys
     (->> (index-query user-id)
          (jdbc/query tx))}}))

;##############################################################################

(defn gpg-key-query [user-id gpg-key-id]
  (-> (sql/select :gpg_keys.*
                  [(-> (sql/select (sql/raw "json_agg(gpg_key_finterprints.fingerprint)"))
                       (sql/from :gpg_key_finterprints)
                       (sql/merge-where 
                         [:= :gpg_key_finterprints.gpg_key_id :gpg_keys.id]))
                   :fingerprints])
      (sql/from :gpg_keys)
      (sql/merge-where [:= :gpg_keys.user_id user-id])
      (sql/merge-where [:= :gpg_keys.id gpg-key-id])
      sql/format))

(defn gpg-key [{tx :tx
                {user-id :user-id 
                 gpg-key-id :gpg-key-id} :route-params}]
  (when-let [k (->> (gpg-key-query user-id gpg-key-id)
                    (jdbc/query tx)
                    first)]
    {:body k}))


;##############################################################################

(defn update-gpg-key [{body :body tx :tx
                       {user-id :user-id gpg-key-id :gpg-key-id} :route-params}]
  (if (= 1  (first (jdbc/update! 
                     tx :gpg_keys (select-keys body [:description]) 
                     ["user_id = ? AND id = ?" user-id gpg-key-id])))
    {:status 204}
    (throw (ex-info "No key has been updated!" {}))))


;##############################################################################

(def gpg-keys-path
  (path :gpg-keys {:user-id ":user-id"}))

(def gpg-keys-add-path
  (path :gpg-keys-add {:user-id ":user-id"}))

(def gpg-key-path 
  (path :gpg-key {:user-id ":user-id"
                  :gpg-key-id ":gpg-key-id"}))

(def routes
  (cpj/routes
    (cpj/GET gpg-key-path [] #'gpg-key)
    (cpj/PATCH gpg-key-path [] #'update-gpg-key)
    (cpj/GET gpg-keys-path [] #'index)
    (cpj/POST gpg-keys-add-path [] #'add-gpg-key)
    (cpj/DELETE gpg-key-path [] #'delete)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
