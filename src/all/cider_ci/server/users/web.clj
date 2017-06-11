(ns cider-ci.server.users.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.users.api-tokens.core :as api-tokens]
    [cider-ci.auth.authorize :as authorize]

    [compojure.core :as cpj]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn dead-end-handler [req]
  {:status 404
   :body "Not found in /users !"})

(defn user? [req]
  (= :user (-> req :authenticated-entity :type)))

(defn authentication-method-not-token? [req]
  (not= :token (-> req :authenticated-entity :authentication-method)))

(defn route-id-matches-signed-in-user-id? [req]
  (= (-> req :authenticated-entity :id str)
     (-> req :route-params :id str)))

(defn authorize? [req]
  (and
    (user? req)
    (authentication-method-not-token? req)
    (route-id-matches-signed-in-user-id? req)))

(def routes
  (cpj/routes
    (cpj/ANY "/users/:id/api-tokens/*" []
             (authorize/wrap-require! api-tokens/routes authorize?))
    (cpj/ANY "*" [] dead-end-handler)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
