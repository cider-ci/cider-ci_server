(ns cider-ci.server.session.oauth
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str deep-merge presence]])

  (:require
    [cider-ci.server.client.constants :refer [CONTEXT]]
    [cider-ci.server.session.oauth.github :as github]
    [cider-ci.server.session.shared :refer [sign-in!]]

    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.open-session.encryptor :refer [encrypt decrypt]]

    [clj-time.core :as time]
    [clojure.string :refer [lower-case trim join]]
    [clojure.walk :refer [stringify-keys keywordize-keys]]
    [compojure.core :as cpj]
    [ring.util.codec :refer [url-encode]]
    [ring.util.response]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn provider-for-request [request]
  (or (get (-> (get-config) :authentication_providers)
           (-> request :form-params
               keywordize-keys :provider-key keyword) nil)
      (get (-> (get-config) :authentication_providers)
           (-> request :route-params :type keyword) nil)))

(defn state-map [request]
  (:form-params  request))

(defn request-sign-in [request]
  (if-let [provider (provider-for-request request)]
    (let [state (encrypt (:secret (get-config)) (state-map request))]
      (github/request-sign-in provider state))
    {:status 422}))

(defn email-addresses-satisfied? [strategy email-addresses]
  (-> (clojure.set/intersection
        (->> strategy :email_addresses (map lower-case) set)
        (->> email-addresses (map lower-case) set))
      empty? not))

(defn sign-in-strategy-satisfied?
  [strategy provider access-token user email-addresses]
  (case (:type strategy)
    "email-addresses" (email-addresses-satisfied?  strategy email-addresses)
    "organization-membership" (github/organization-memebership-satisfied?
                                strategy provider user access-token)
    "team-membership" (github/team-memebership-satisfied?
                        strategy provider user access-token)))


; ##########

(defn create-or-remap-email-addresses [user email-addresses tx]
  (doseq [email-address email-addresses]
    (if-not (->> ["SELECT true FROM email_addresses WHERE email_address= lower(?)"
                  email-address]
                 (jdbc/query tx) first)
      (jdbc/insert! tx :email_addresses
                    {:user_id (:id user)
                     :email_address email-address})
      (jdbc/update! tx :email_addresses
                    {:user_id (:id user)
                     :email_address email-address}
                    ["email_address = lower(?)" email-address]))))

(defn create-or-update-user [user-properties strategy provider-config email-addresses]
  (let [login (str (:login user-properties) "@" (:name provider-config))
        github-id (:id user-properties)]
    (assert (presence login))
    (assert (presence github-id))
    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (when-not (->> ["SELECT true FROM users WHERE github_id = ?" github-id]
                     (jdbc/query tx) first)
        (jdbc/insert! tx :users
                      (merge (:create_attributes  strategy)
                             {:login login
                              :github_id (:id user-properties)})))
      (jdbc/update! tx :users
                    (merge (:update_attributes strategy)
                           {:login login})
                    ["github_id = ?" github-id])
      (let [user (->> ["SELECT * FROM users WHERE github_id = ?" github-id]
                      (jdbc/query tx) first)]
        (create-or-remap-email-addresses user email-addresses tx)
        user))))


; ##########

(defn sign-in [request]
  (if-let [provider (provider-for-request request)]
    (let [access-token (github/get-token provider request)
          user (github/get-userproperties provider access-token)
          email-addresses (->> (github/get-email-addresses provider access-token)
                               (filter :verified)
                               (map :email))]
      (if-let [strategy (->> (:sign_in_strategies provider)
                             lazy-seq
                             (filter #(sign-in-strategy-satisfied?
                                        % provider access-token user email-addresses))
                             first)]
        (let [user (create-or-update-user user strategy provider email-addresses)
              state (decrypt (:secret (get-config)) (-> request :query-params :state))
              redirect-target (or (-> state :current-url presence)
                                  (str CONTEXT "/"))]
          (logging/info {:token access-token
                         :state state
                         :user user
                         :email-addresses email-addresses
                         :successful-strategy strategy
                         :provider-config provider
                         :request request
                         })
          (sign-in! user redirect-target))
        {:status 403
         :body "No sign in strategy succeeded!"}))
    {:status 422
     :body "Authentication provider not found."}))

;(debug/re-apply-last-argument #'provider-for-request)
;(ns-publics 'logbug.debug)
;(debug/debug-ns *ns*)
