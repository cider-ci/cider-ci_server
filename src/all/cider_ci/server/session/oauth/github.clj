(ns cider-ci.server.session.oauth.github
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str deep-merge presence]])

  (:require
    [cider-ci.server.client.constants :refer [CONTEXT]]

    [clj-http.client :as http-client]
    [clojure.string :refer [lower-case trim join]]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
  ))

; ##########

; TODO move to clj-utils

(def ^:private default-opts
  {:content-type :json
   :accept :json
   :as :auto})

(declare get-seq)

(defn- get-seq-next [resp opts xs]
  (lazy-seq
    (if-let [[x] (seq xs)]
      (cons x (get-seq-next resp opts (rest xs)))
      (when-let [next-url (-> resp :links :next)]
        (get-seq next-url opts)))))

(defn get-seq [url opts]
  (let [resp (http-client/get url (deep-merge default-opts opts))]
    (assert (= (:status resp) 200))
    (get-seq-next resp opts (:body resp))))


; ##########


(defn get-userproperties [provider token]
  (-> (http-client/get
        (str (trim (:api_endpoint provider)) "/user")
        {:oauth-token token
         :accept :json
         :as :json})
      :body))


(defn request-sign-in [provider-config state]
  (let [url (str (trim (:oauth_base_url provider-config)) "/authorize"
                 "?" (http-client/generate-query-string
                       {:state state
                        :client_id (:client_id provider-config)
                        :scrope "user:email,read_org"}))]
    (ring.util.response/redirect url)))

(defn get-token [config request]
  (-> (http-client/post
        (str (trim (:oauth_base_url config)) "/access_token?"
             (http-client/generate-query-string
               {:client_id (:client_id config)
                :client_secret (:client_secret config)
                :state (-> request :params :state)
                :code (-> request :params :code)}))
        {:accept :json
         :as :json})
      :body :access_token))


(defn get-email-addresses [provider token]
  (get-seq (str (trim (:api_endpoint provider))
                "/user/emails")
           {:oauth-token token}))


(defn presence! [x]
  (or (presence x)
      (throw (IllegalStateException. "No presence!"))))

(defn organization-memebership-satisfied? [strategy provider user access-token]
  (let [url (->> [(:api_endpoint provider) "/orgs/"
                  (:organization_login strategy) "/members/"
                  (:login user)]
                 (map trim) (map presence!) join)
        token (or (-> strategy :access_token presence) access-token)
        resp (http-client/get
               url {:throw-exceptions false
                    :accept :json
                    :as :auto
                    :oauth-token token})]
    (cond (= 404 (:status resp)) false
          (<= 200 (:status resp) 399) true
          :else (-> "Unexpected response for testing organization membership!"
                    (ex-info {:response resp}) throw))))

(defn team-of-org [strategy provider token]
  (->> (get-seq (->> [(:api_endpoint provider) "/orgs/"
                      (:organization_login strategy) "/teams"]
                     (map trim) (map presence!) join)
                {:oauth-token token})
       (filter #(= (:name %) (:team_name strategy)))
       first))

(defn team-memebership-satisfied? [strategy provider user access-token]
  (when-let [token (or (-> strategy :access_token presence)
                       (presence access-token))]
    ; we need the team of the org first
    (when-let [team (team-of-org strategy provider token)]
      ; now we can check membership
      (let [url (->> [(:api_endpoint provider) "/teams/"
                      (:id team) "/memberships/" (:login user)]
                     (map trim) (map presence!) join)
            resp (http-client/get
                   url {:throw-exceptions false
                        :accept :json
                        :as :auto
                        :oauth-token token})]
        (cond
          (= 404 (:status resp)) false
          (<= 200 (:status resp) 299) true
          :else (-> "Unexpected response for testing team membership!"
                    (ex-info {:response resp})
                    throw))))))

;(debug/debug-ns *ns*)
