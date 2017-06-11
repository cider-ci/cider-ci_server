(ns cider-ci.server.executors.web
  (:refer-clojure :exclude [str keyword update])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.executors.state :as state]
    [cider-ci.server.executors.shared :refer [allowed-keys]]


    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]

    [cider-ci.server.executors.token :as token]
    [compojure.core :as cpj]
    [clojure.java.jdbc :as jdbc]


    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


;;; delete ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete! [id]
  (or (= 1 (-> (jdbc/delete! (rdbms/get-ds)
                           :executors ["id = ? " id]) first))
      (throw (ex-info "Executor delete failed" {:id id})))
  (state/update-executors)
  {:status 204})


;;; create ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn insert [params]
  (first (jdbc/insert! (rdbms/get-ds) :executors params)))

(defn create [params]
  (let [token (or (-> params :token presence)
                  (token/create))
        token_hash (token/hash token)
        token_part (subs token 0 5)]
    (if-not (token/valid? token)
      {:status 422
       :body (str "The token must consist of alphanumeric characters "
                  " (with optional = padding) "
                  " and the length of the token must be between "
                  " 16 and 64 characters!")}
      (if-let [executor (-> params (select-keys (conj allowed-keys :id))
                            (assoc :token_hash token_hash
                                   :token_part token_part)
                            insert)]
        (do (state/update-executors)
            {:status 201
             :body (assoc executor :token token)})
        {:status 422}))))

;;; patch ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update! [id params]
  (or (= 1 (-> (jdbc/update! (rdbms/get-ds)
                             :executors params ["id = ? " id]) first))
      (throw (ex-info "Executor update failed" {:params params :id id}))))

(defn patch [id params]
  (let [token (-> params :token presence)
        update-params (merge (select-keys params allowed-keys)
                             (when token
                               {:token_hash (token/hash token)
                                :token_part (subs token 0 5)}))]
    (update! id update-params)
    (state/update-executors)
    {:status 204}))


;;; put ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn put [id params]
  (if (->> ["SELECT true FROM executors WHERE id = ?" id]
           (jdbc/query (rdbms/get-ds))
           first)
    (patch id params)
    (create (assoc params :id id))))


;;; get ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-executor [id]
  (if-let [executor (get @state/db* (keyword id))]
    {:status 200
     :body {:executor executor}}
    {:status 404
     :body "executor not found"}))


;;; index ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn index [req]
  {:status 200
   :body {:executors @state/db*}})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private routes*
  (cpj/routes
    (cpj/GET "/executors/" [] index)
    (cpj/POST "/executors/" {body :body} (create body))

    (cpj/GET "/executors/:id" [id] (get-executor id))
    (cpj/PUT "/executors/:id" {{id :id} :route-params body :body} (put id body))
    (cpj/PATCH "/executors/:id" {{id :id} :route-params body :body} (patch id body))
    (cpj/DELETE "/executors/:id" [id] (delete! id))
    ))


(defn routes [request]
  (if (#{:post :put :patch :delete} (:request-method request))
    ((authorize/wrap-require! routes* {:admin true}) request)
    ((authorize/wrap-require! routes* {:user true}) request)))





;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
