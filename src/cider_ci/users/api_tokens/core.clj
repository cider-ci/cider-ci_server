(ns cider-ci.users.api-tokens.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [clojure.data.codec.base64 :as base64]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [pandect.algo.sha256 :as algo.sha256]
    [ring.util.response]
    [clj-time.format :as time-format]
    [clj-time.core :as time]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [com.google.common.io BaseEncoding]
    [java.time OffsetDateTime]
    [org.joda.time DateTime]
    ))

;(time-format/unparse (time-format/formatters :basic-date-time) (time/now))
;(time-format/unparse (time-format/formatters :date-time) (time/now))
;(time-format/parse (time-format/formatters :date-time) "2018-05-18T17:17:25+02:00")
;(time-format/parse (time-format/formatters :date-time) "2017-05-18T17:15:00.437Z")
;(time-format/show-formatters)
; (OffsetDateTime/parse "2018-05-18T17:17:25+02:00")
; (time/date-time (.toEpochSecond (OffsetDateTime/parse "2018-05-18T17:17:25Z")))
; (type (time/now))

; (DateTime. (* 1000 (.toEpochSecond (OffsetDateTime/parse "2018-05-18T17:17:25+02:00"))))

; (DateTime. (* 1000 1526663845))


(defn dead-end-handler [req]
  {:status 404
   :body "Not found in /cider-ci/users/:id/api-tokens/ !"})

(defn index [req]
  (let [user-id (-> req :route-params :user-id)
        tokens (jdbc/query (get-ds)
                           ["SELECT * FROM api_tokens WHERE user_id = ?" user-id])]
    {:status 200
     :body {:api-tokens (->> tokens
                             (map (fn [t] [(:id t) t]))
                             (into {}))}}))

;;; create ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-iso8601 [s]
  (DateTime. (* 1000 (.toEpochSecond (OffsetDateTime/parse s)))))

(defn create-or-update-params [params]
  (->> params
       (map (fn [[k v]]
              (case k
                :expires_at [k (parse-iso8601 v)]
                [k v])))
       (into {})))


(defn hash-string [s]
  (->> s
       algo.sha256/sha256-bytes
       base64/encode
       (map char)
       (apply str)))

(def b32 (BaseEncoding/base32))

(defn secret [n]
  (->> n crypto.random/bytes
       (.encode b32)
       (map char)
       (apply str)))

(defn create [req]
  (let [user-id (-> req :route-params :user-id)]
    ;TODO check permissions here: user-id either signed in user or admin
    (let [secret (secret 20)
          token-hash (hash-string secret)
          create-params (assoc (dissoc (-> req :body) :id)
                                :token_hash token-hash
                                :token_part (subs secret 0 5)
                                :user_id user-id)
          params (create-or-update-params create-params)
          token (first (jdbc/insert!  (get-ds) :api_tokens params))]
      {:status 201
       :body (assoc token
                    :secret secret)})))

(def update-keys
  [:revoked :description :expires_at
   :scope_read :scope_write :scope_admin_read :scope_admin_write])

(defn find-token [api-token-id]
  (->> ["SELECT * FROM api_tokens WHERE id = ?" api-token-id]
       (jdbc/query (get-ds)) first))

(defn update-token [api-token-id params]
  (->> ["id = ?" api-token-id]
       (jdbc/update! (get-ds) :api_tokens (create-or-update-params params))))

(defn patch [req]
  (let [api-token-id (-> req :route-params :api-token-id)]
    (if-let [api-token (find-token api-token-id)]
      (if (:revoked api-token)
        {:status 422}
        (if (= 1 (first (update-token api-token-id
                                      (select-keys (:body req) update-keys))))
          {:status 200
           :body "OK"}
          {:status 422}))
      {:status 404})))

(def routes
  (cpj/routes
    (cpj/GET "/users/:user-id/api-tokens/" [] index)
    (cpj/POST "/users/:user-id/api-tokens/" [] create)
    (cpj/PATCH "/users/:user-id/api-tokens/:api-token-id" [] patch)
    (cpj/ANY "*" [] dead-end-handler)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
