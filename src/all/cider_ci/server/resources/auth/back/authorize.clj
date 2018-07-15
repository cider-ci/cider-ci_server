(ns cider-ci.server.resources.auth.back.authorize
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]))

(def HTTP-SAFE-VERBS #{:get :head :options :trace})
(def HTTP-UNSAFE-VERBS #{:post :put :delete :patch})

(defn is-not-admin? [request]
  (-> request :authenticated-entity :is_admin not))

(defn handler-is-ignored? [ignore-handler-keys request]
  (boolean (when-let [handler-key (:handler-key request)]
             (handler-key ignore-handler-keys))))

(defn authenticated-entity-not-present? [request]
  (not (contains? request :authenticated-entity)))

(defn violates-admin-write-scope? [request]
  (boolean 
    (and ((:request-method request) 
          #{:post :put :delete :patch})
         (-> request :authenticated-entity 
             :scope_admin_write not))))

(defn admin-and-safe? [request]
  (logging/debug ((:request-method request) HTTP-SAFE-VERBS))
  (logging/debug (-> request :authenticated-entity :is_admin))
  (boolean
    (and ((:request-method request) HTTP-SAFE-VERBS)
         (-> request :authenticated-entity :is_admin))))

(defn admin-write-scope-and-unsafe? [request]
  (boolean
    (and ((:request-method request) HTTP-UNSAFE-VERBS)
         (-> request :authenticated-entity :is_admin))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
